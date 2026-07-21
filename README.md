# CEI - Ceketrum Enhanced Inventory

[![CurseForge](https://img.shields.io/badge/CurseForge-CEI-orange?style=flat-square&logo=curseforge)](https://www.curseforge.com/minecraft/mc-mods/ceketrum-enough-items)
[![Modrinth](https://img.shields.io/badge/Modrinth-CEI-green?style=flat-square&logo=modrinth)](https://modrinth.com/mod/ceketrum-enough-items)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x--26.2-62B74A?style=flat-square&logo=minecraft)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-Supported-6271a0?style=flat-square)](https://fabricmc.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-Supported-D33030?style=flat-square)](https://neoforged.net/)
[![License](https://img.shields.io/badge/License-ECLIPTEA--1.0-4A90E2?style=flat-square)](LICENSE)

**CEI (Ceketrum Enhanced Inventory)** is a modern, high-performance item browser, recipe viewer, and inventory overlay mod for Minecraft. Built with a modular multi-version architecture, CEI provides an intuitive alternative to JEI/REI/EMI with advanced recipe pinning, instant search, and seamless cross-version compatibility for **Fabric** and **NeoForge**.

---

## 🔗 Official Downloads & Links

- **CurseForge**: [https://www.curseforge.com/minecraft/mc-mods/ceketrum-enough-items](https://www.curseforge.com/minecraft/mc-mods/ceketrum-enough-items)
- **Modrinth**: [https://modrinth.com/mod/ceketrum-enough-items](https://modrinth.com/mod/ceketrum-enough-items)
- **Discord Community**: [https://discord.gg/FFG9c6wCzv](https://discord.gg/FFG9c6wCzv)

---

## 🌟 Key Features

### 🔍 Advanced Item Browser
- **Instant Search Bar**: Filter items dynamically by item name, mod ID (`@modid`), or tooltips.
- **Favorites & Star System**: Bookmark your most frequently used items and filter your browser to view favorites only.
- **Smooth Panel Animations**: Slide animations with configurable positions (left/right), adjustable widths, and GUI scale responsiveness.
- **Data Component & Variant Support**: Full preview for potions, enchanted items, custom NBT/components, and modded items.

### 📖 Comprehensive Recipe Viewer (`R` & `U` Hotkeys)
- **Recipes & Usages**: Hover over any item in your inventory or CEI panel and press **`R`** to view recipes or **`U`** to view usages.
- **Multiple Recipe Categories**:
  - 🛠️ **Crafting Table**: Shaped and shapeless 3x3 / 2x2 recipes.
  - 🔥 **Smelting & Furnace**: Cooking times, fuel requirements, and output yields.
  - 🧪 **Brewing Stand**: Ingredient paths and potion alchemy recipes.
  - 🪨 **Stonecutter**: Block cutting variants.
  - 🛡️ **Smithing Table**: Armor and tool upgrade templates.
  - ⚙️ **Custom Mod Machines**: Automatic fallback icons for modded machine recipes.
- **Recursive Item Navigation**: Left-click ingredients inside recipe screens to explore their recipes, or right-click to view their usages.

### 📌 Pinned Recipe Cards (HUD Overlay)
- **Pin Any Recipe**: Click the pin icon in the recipe screen to attach recipe cards directly to your active game screen!
- **Interactive Drag & Drop**: Click and drag pinned cards anywhere on screen. Position is saved automatically.
- **HUD Visibility & Opacity**: Toggle card visibility on the game HUD and cycle opacity levels for clean gameplay.

### ⚡ Crafting Auto-Transfer (`+` Button)
- **One-Click Auto-Fill**: Click the **`+`** button in the crafting recipe view to transfer required ingredients directly into your open Crafting Table.
- **Max Stack Fill**: Hold `Shift` or `Right-Click` the **`+`** button to fill the maximum possible crafting stacks from your inventory.

### 📜 Item Descriptions, Loot Tables & World Data
- **Descriptions & Stats Tab**: Displays lore text, max stack size, durability, enchantment value, and localized item descriptions (`en_us`, `fr_fr`, etc.).
- **Loot Table Drops**: View mob drops, chest loot, and block drop sources.
- **World & Biome Locations**: Index biomes and structure spawn locations for blocks and items.

### ⚙️ In-Game Configuration Screen
- Access settings via the gear icon in the side panel header to customize panel positions, animation speeds, display modes, keybinds, and search options.

---

## ⚡ Multi-Version & Dual-Mixin Architecture

CEI features a dynamic versioning system capable of supporting multiple Minecraft releases (from **1.21.x** up to **26.1 / 26.2**):

- **Runtime Version Detection**: Uses an internal Mixin plugin (`CeiMixinConfigPlugin`) to detect game engine changes dynamically.
- **Dual Pipeline Support**: Seamlessly bridges classic `GuiGraphics` rendering (1.21 / 26.1) and Mojang's updated `GuiGraphicsExtractor` deferred rendering pipeline (26.2).
- **Cross-Platform**: Native builds available for both **Fabric Loader** and **NeoForge**.

---

## 📋 Requirements

| Platform | Requirements |
| :--- | :--- |
| **Minecraft** | 1.21.x, 26.1, 26.1.1, 26.1.2, 26.2 |
| **Mod Loaders** | Fabric Loader (≥0.18.4) or NeoForge |
| **Dependencies** | Fabric API (for Fabric builds) |
| **Java Version** | Java 21 or higher |

---

## 🚀 Installation

1. Ensure you have **Fabric Loader** or **NeoForge** installed for your target Minecraft version.
2. Download **CEI** from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ceketrum-enough-items) or [Modrinth](https://modrinth.com/mod/ceketrum-enough-items).
3. If using Fabric, download [Fabric API](https://modrinth.com/mod/fabric-api).
4. Place the downloaded `.jar` file(s) into your Minecraft `.minecraft/mods` directory.
5. Launch Minecraft and press `R` or open any container inventory to start browsing!

---

## 🎮 Default Controls

| Action | Key / Input |
| :--- | :--- |
| **View Recipes** | Hover over item + **`R`** (or Left-Click item in CEI panel) |
| **View Usages** | Hover over item + **`U`** (or Right-Click item in CEI panel) |
| **Toggle Favorite** | `Shift` + Left-Click item in CEI panel |
| **Auto-Fill Crafting** | Click **`+`** button in Crafting Recipe Screen |
| **Fill Max Stack** | `Shift` + Click or Right-Click **`+`** button |
| **Close Recipe Screen** | `Esc` or Inventory Key (`E`) |

---

## 🛠️ Building from Source

To build CEI for all supported target modules:

```bash
git clone https://github.com/ceketrum/cei.git
cd cei
./gradlew build
```

To build a specific module (e.g., G7 Fabric and NeoForge):

```bash
./gradlew :G7:fabric:build :G7:neoforge:build
```

Compiled JAR artifacts will be located in each module's `build/libs/` folder.

---

## 📄 License

CEI is licensed under the **ECLIPTEA-1.0 License**. See [LICENSE](LICENSE) for details.

---

## 🙏 Acknowledgements

- Built on the **Ecliptea** modding framework by Ceketrum.
- Designed with inspiration from JEI, REI, and EMI for the Minecraft modding community.
