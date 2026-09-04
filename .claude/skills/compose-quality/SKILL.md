---
name: compose-quality
description: Write and review Jetpack Compose UI correctly — state hoisting, the Modifier contract, effect handling, lifecycle-aware collection, stability and recomposition performance. Use this whenever writing, editing, refactoring, or reviewing any @Composable, screen, or UI component in a Compose codebase, and whenever the user mentions recomposition, jank, scroll performance, Compose state, or "why does this keep redrawing". Use it before writing new UI, not only when something is broken.
---

# Compose quality

For the full review checklist and diagnostic greps, read
`../android-audit/references/compose.md`. This file holds the authoring patterns.

## The shape of a screen

Split every screen into a stateful route and a stateless content composable. The stateless half is what
you can preview, screenshot-test, and reuse.

```kotlin
@Composable
fun HomeRoute(
    onNavigateToDetail: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onItemClick = onNavigateToDetail,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        HomeUiState.Loading -> LoadingIndicator(modifier)
        is HomeUiState.Error -> ErrorState(uiState.message, onRefresh, modifier)
        is HomeUiState.Content -> ItemList(uiState.items, onItemClick, modifier)
    }
}
```

Note the sealed state: `Loading`/`Error`/`Content` makes impossible combinations unrepresentable. A single
data class with `isLoading`, `error`, and `items` all nullable lets you render a spinner over an error
over stale data, and eventually you will.

## Rules with reasons

**Modifier is the first optional parameter, named `modifier`, defaulted, applied to the root node, used
once.** Callers rely on this to control layout. Applying it to an inner node silently ignores the caller's
padding and makes the bug look like theirs.

**State flows down, events flow up.** A component that owns state it doesn't need can't be reused or
tested. Hoist to the lowest common ancestor that needs it.

**`collectAsStateWithLifecycle()`, never `collectAsState()`.** The latter keeps collecting while
backgrounded — the upstream flow (polling, location, DB observation) keeps running.

**Immutable state types.** `List<T>` as a composable parameter makes the composable unskippable, because
the compiler can't know the list won't mutate. Use `kotlinx.collections.immutable`:

```kotlin
data class HomeUiState(val items: ImmutableList<Item> = persistentListOf())
```

Only annotate `@Stable`/`@Immutable` when the guarantee is actually true. A lying annotation produces UI
that silently fails to update — much harder to debug than a slow screen.

**Read state as late as possible.** Reading a fast-changing value in a parent recomposes the subtree. Push
it into layout or draw:

```kotlin
// Recomposes on every scroll pixel
Box(Modifier.offset(y = scrollState.value.dp))

// Skips composition; only layout re-runs
Box(Modifier.offset { IntOffset(0, scrollState.value) })
```

Same idea for `graphicsLayer { }`, `drawBehind { }`, and lambda-based `background`. This is the standard
fix for scroll jank.

**Effects need honest keys.** `LaunchedEffect(Unit)` means "run once ever". If the body reads a value that
changes, key on it, or you'll capture a stale one forever.

**Lazy lists need keys.** Without `key`, item state binds to position, so deleting item 2 makes item 3
inherit its state. Also kills item animations and reuse.

```kotlin
LazyColumn {
    items(items = uiState.items, key = { it.id }) { item -> ItemRow(item) }
}
```

**Don't compute in the composable body.** Sorting, filtering, formatting, and date parsing re-run on every
recomposition. Move to the ViewModel, or `remember(key)` it.

## Before saying it's done

- Does it have a `@Preview` (light + dark)?
- Does it survive rotation and process death?
- Icon-only buttons: `contentDescription` set, touch target ≥48dp?
- Text in `sp`, layout intact at 200% font scale?
- If you claimed a performance win, did you measure it? Compose compiler metrics or Layout Inspector
  recomposition counts — otherwise mark it as unverified rather than asserting it.
