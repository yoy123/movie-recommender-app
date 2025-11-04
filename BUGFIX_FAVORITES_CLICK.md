# Bug Fix: Favorites Selection vs. Removal

## Issue
When clicking a movie in the Dee's Favorites section:
- ❌ Clicking anywhere on the card toggled selection
- ❌ Clicking the X button to remove also triggered selection toggle
- ❌ Result: Trying to unselect a movie would remove it from favorites

## Root Cause
The `Card` component had `onClick = onToggleSelection`, and the remove `IconButton` was inside the card. When clicking the IconButton, the click event would bubble up to the Card, triggering both:
1. Remove from favorites (`onRemove`)
2. Toggle selection (`onToggleSelection`)

## Solution

### Changed Click Handling
```kotlin
// OLD (Problematic)
Card(
    onClick = onToggleSelection,  // Card handles click
    ...
) {
    Box {
        IconButton(onClick = onRemove) // Also triggers card click!
    }
}

// NEW (Fixed)
Card(
    onClick = {},  // Disable card click
    ...
) {
    Box(
        modifier = Modifier.clickable { onToggleSelection() }  // Box handles selection
    ) {
        Surface(
            modifier = Modifier.clickable { onRemove() }  // Separate handler
        ) {
            Icon(Close)  // Remove button
        }
    }
}
```

### Key Changes
1. **Disabled Card onClick**: Set to empty lambda `{}`
2. **Box handles selection**: Added `.clickable { onToggleSelection() }` to Box
3. **Separate remove handler**: Changed IconButton to Surface with its own `.clickable { onRemove() }`
4. **Click event isolation**: Each clickable element has its own handler

## User Experience

### Before Fix:
```
User Action                    Result
─────────────────────────────────────────────────
Tap movie poster          →    ✓ Selects for recommendations
Tap X button              →    ✗ Removes from favorites AND toggles selection
Try to unselect           →    ✗ Movie disappears from favorites!
```

### After Fix:
```
User Action                    Result
─────────────────────────────────────────────────
Tap movie poster          →    ✓ Selects for recommendations
Tap X button              →    ✓ Only removes from favorites
Try to unselect           →    ✓ Just unselects, stays in favorites
```

## Technical Details

### Clickable Hierarchy
```
Card (onClick disabled)
  └─ Box (clickable: toggle selection)
      ├─ AsyncImage (poster)
      ├─ Surface (selection checkmark)
      └─ Surface (clickable: remove from favorites)
          └─ Icon (X)
```

### Click Event Propagation
- **Before**: IconButton click → Card click (both handlers execute)
- **After**: Surface click → Stops at Surface (only one handler executes)

### Component Structure
```kotlin
Box(modifier = Modifier.clickable { onToggleSelection() })
    ↓ Clicking poster area → Toggles selection
    
Surface(modifier = Modifier.clickable { onRemove() })
    ↓ Clicking X button → Removes from favorites only
```

## Files Modified

✏️ `ui/screens/FavoritesScreen.kt`
- Added `import androidx.compose.foundation.clickable`
- Refactored `FavoriteMovieCard` composable:
  - Disabled Card onClick
  - Added Box clickable for selection
  - Changed IconButton to Surface with separate clickable
  - Updated Icon padding

## Testing Checklist

To verify the fix:
1. ✅ Open Dee's Favorites
2. ✅ Select a favorite movie (should show checkmark)
3. ✅ Click selected movie again (should unselect, stay in favorites)
4. ✅ Click X button on unselected movie (should remove from favorites)
5. ✅ Click X button on selected movie (should only remove, not toggle)
6. ✅ Verify removed movies don't appear in favorites
7. ✅ Verify unselected movies still appear in favorites

## Benefits

1. **Intuitive UX**: X button only removes, poster only selects
2. **No accidental deletion**: Can't accidentally remove while trying to unselect
3. **Clear feedback**: Each action has one clear result
4. **Better control**: Users have precise control over actions

## Related Components

This fix ensures consistency with:
- `MovieCard` in MovieSelectionScreen (already had proper click handling)
- `SearchResultMovieCard` in FavoritesScreen (add-only, no conflicts)

---

**Status:** ✅ Fixed and ready for testing
