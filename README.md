## Features

- **Lock Maps** - Prevent your maps from being copied
- **Owner-Only Unlock** - Only the map owner can unlock it
- **Anonymous Mode** - Lock maps without revealing your name
---

## Commands

| Command | Description |
|---------|-------------|
| `/maplock` | Lock/unlock the map you're holding |
| `/maplock anon` | Lock map anonymously (hides owner name) |
| `/maplock reload` | Reload configuration |

**Aliases:** `/ml`, `/lockmap`

---
## How It Works

1. **Hold a filled map** in your main hand
2. Type `/maplock` to lock it
3. The map now displays a "locked" indicator in its lore
4. Nobody can copy this map through any crafting method
5. Only you can unlock it by using `/maplock` again

---


## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `maplock.use` | Lock and unlock maps | Everyone |
| `maplock.reload` | Reload config | OP |
| `maplock.*` | All permissions | OP |
