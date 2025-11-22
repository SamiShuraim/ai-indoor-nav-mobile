# Assignment Display Format Examples

## Visual Layout

```
┌─────────────────────────────────────────┐
│                                         │
│         MAP VIEW WITH BLUE DOT          │
│                                         │
│                                         │
│                                         │
│                                         │
│                                         │
│                                         │
│                                         │
│  ┌─────────────────────┐                │
│  │ 🚶 Ground Floor | 45 | ✅ │           │
│  └─────────────────────┘                │
└─────────────────────────────────────────┘
```

**Position**: Bottom Left Corner  
**Background**: Semi-transparent dark (existing floor selector style)  
**Text Color**: White  
**Font**: Bold, 15sp  

## Format Breakdown

### Structure:
```
[Health Icon] [Floor Name] | [Age Number] | [Status Icon]
```

### Symbols:
- `🚶` = Walking person (enabled/healthy)
- `♿` = Wheelchair (disabled/accessibility needs)
- `✅` = Enabled (no accessibility issues)
- `⚠️` = Disabled (accessibility considerations needed)
- `|` = Separator (visual divider)

## Real Examples

### Enabled Users:

```
🚶 Ground Floor | 18 | ✅
```
*Young adult, no accessibility needs*

```
🚶 First Floor | 35 | ✅
```
*Middle-aged, no accessibility needs*

```
🚶 Second Floor | 58 | ✅
```
*Older adult, no accessibility needs*

```
🚶 Third Floor | 90 | ✅
```
*Elderly but mobile*

### Disabled Users:

```
♿ Ground Floor | 22 | ⚠️
```
*Young adult with accessibility needs*

```
♿ First Floor | 45 | ⚠️
```
*Middle-aged with accessibility needs*

```
♿ Second Floor | 67 | ⚠️
```
*Senior with accessibility needs*

```
♿ Third Floor | 84 | ⚠️
```
*Elderly with accessibility needs*

## Space Comparison

### Old Format (REMOVED):
```
🚶 Floor: Ground Floor | Age: 45 | Status: Enabled
```
**Character count**: 52 characters  
**Width**: ~350-400px on screen

### New Format (CURRENT):
```
🚶 Ground Floor | 45 | ✅
```
**Character count**: 26 characters (50% reduction!)  
**Width**: ~200-250px on screen (40% narrower)

## Multi-Floor Examples

When switching between floors, the display updates:

```
User on Ground Floor:
┌─────────────────────────┐
│ 🚶 Ground Floor | 32 | ✅ │
└─────────────────────────┘

User switches to First Floor (new assignment):
┌─────────────────────────┐
│ ♿ First Floor | 71 | ⚠️   │
└─────────────────────────┘

User switches to Second Floor (new assignment):
┌─────────────────────────┐
│ 🚶 Second Floor | 26 | ✅ │
└─────────────────────────┘
```

## Interaction

### Button Click (Top Right):
```
     ┌───────┐
     │   📋   │  ← Assignment Button
     └───────┘

User clicks → New assignment generated → Display updates
```

### Before Click:
```
🚶 Ground Floor | 45 | ✅
```

### After Click:
```
♿ Ground Floor | 68 | ⚠️
```
*Age and status randomly regenerated*

## Edge Cases

### Unknown Floor:
```
🚶 ? | 45 | ✅
```
*Fallback when floor name unavailable*

### Long Floor Names:
```
🚶 Administration Building L3 | 45 | ✅
```
*Container auto-adjusts width*

### Very Short Floor Names:
```
🚶 G | 45 | ✅
```
*Still looks good with compact names*

## Accessibility Notes

### Color Independence:
- Uses distinct emojis, not just colors
- ✅ and ⚠️ provide visual difference beyond color
- Bold white text on dark background (high contrast)

### Emoji Recognition:
- Universal symbols (walking, wheelchair)
- Check mark and warning triangle are standard
- No language barriers

### Information Density:
```
3 key pieces of info in ~25 characters:
1. Health status (emoji)
2. Location (floor name)
3. Age (number)
4. Accessibility needs (icon)
```

## Animation/Behavior

### On Assignment Update:
1. Old display fades out (100ms)
2. New display fades in (100ms)
3. Total transition: 200ms

### On Floor Change:
1. Display hidden immediately
2. Position updates
3. New assignment requested
4. Display appears with new info

### On Button Click:
1. Button pressed animation
2. "New assignment received" toast message
3. Display updates with new values

## Technical Details

### Layout Properties:
- `layout_gravity`: `bottom|start` (bottom left)
- `layout_width`: `wrap_content` (auto-size)
- `layout_height`: `wrap_content`
- `layout_marginStart`: `16dp`
- `layout_marginBottom`: `16dp`
- `padding`: `10dp`
- `background`: Semi-transparent dark with rounded corners

### Text Properties:
- `textSize`: `15sp`
- `textColor`: White
- `textStyle`: Bold
- `gravity`: Default (start)

## Summary

✅ **Compact**: 50% fewer characters than before  
✅ **Clear**: Emojis convey meaning instantly  
✅ **Positioned**: Bottom left corner  
✅ **Responsive**: Auto-adjusts to content  
✅ **Accessible**: High contrast, universal symbols  
✅ **Efficient**: Minimal screen space usage  
