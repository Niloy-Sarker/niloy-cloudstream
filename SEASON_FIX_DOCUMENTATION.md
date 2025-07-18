# DhakaFlix-tmdb TV Series Season Handling Fix

## Problem
The current implementation was processing TV series folders sequentially and assigning season numbers (1, 2, 3, etc.) in the order they appear. This meant that if there was a folder named "OVA" before "Season 1", the OVA would become Season 1 in the app, and the actual Season 1 would become Season 2.

## Solution
Enhanced the season parsing logic to:

1. **Parse actual season numbers** from folder names when they follow common patterns:
   - "Season 1", "Season 2", etc.
   - "S1", "S2", etc.
   - "Series 1", "Series 2", etc.

2. **Handle custom season names** for non-standard folders:
   - "OVA", "Specials", "Movies", "Extras", etc.
   - These appear in the season selector with their actual folder names

3. **Maintain backward compatibility** with existing folder structures

## Implementation Details

### New Function: `parseSeasonInfo()`
- Takes a folder name and returns a `Pair<Int?, String?>`
- Returns `(seasonNumber, null)` for standard season folders
- Returns `(null, customName)` for non-standard folders
- Handles case-insensitive matching and common variations

### Modified Logic
- When processing TV series folders, extract season info instead of auto-incrementing
- Pass both season number and custom name to the `seasonExtractor` function
- Include custom season names in episode names for better user experience

### Examples of How It Works

**Before (Auto-increment):**
```
Folder: "OVA" -> Season 1
Folder: "Season 1" -> Season 2
Folder: "Season 2" -> Season 3
```

**After (Smart parsing):**
```
Folder: "OVA" -> Custom season "OVA"
Folder: "Season 1" -> Season 1
Folder: "Season 2" -> Season 2
```

## Folder Name Patterns Supported

### Standard Season Patterns (extracts season number):
- "Season 1", "Season 2", "Season 10"
- "S1", "S2", "S10"
- "Series 1", "Series 2"
- Case-insensitive matching

### Custom Season Names (uses folder name as display):
- "OVA"
- "Specials"
- "Movies"
- "Extras"
- Any folder name that doesn't match standard patterns

## Benefits
1. **Correct season numbering** - Seasons appear in proper order
2. **Better user experience** - Custom content (OVA, Specials) is clearly labeled
3. **Backward compatibility** - Existing folder structures still work
4. **Flexible** - Handles various naming conventions

## Files Modified
- `DhakaFlix-tmdb/src/main/kotlin/com/niloy/BdixDhakaFlix14Provider.kt`
  - Added `parseSeasonInfo()` function
  - Modified TV series processing logic
  - Updated `seasonExtractor()` function signature
  - Enhanced episode naming for custom seasons
