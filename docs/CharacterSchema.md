
# CharacterSchema

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **kotlin.String** | Name of the character. | 
**account** | **kotlin.String** | Account name. | 
**skin** | [**CharacterSkin**](CharacterSkin.md) | Character skin code. | 
**level** | **kotlin.Int** | Combat level. | 
**xp** | **kotlin.Int** | The current xp level of the combat level. | 
**maxXp** | **kotlin.Int** | XP required to level up the character. | 
**gold** | **kotlin.Int** | The numbers of gold on this character. | 
**speed** | **kotlin.Int** | *Not available, on the roadmap. Character movement speed. | 
**miningLevel** | **kotlin.Int** | Mining level. | 
**miningXp** | **kotlin.Int** | The current xp level of the Mining skill. | 
**miningMaxXp** | **kotlin.Int** | Mining XP required to level up the skill. | 
**woodcuttingLevel** | **kotlin.Int** | Woodcutting level. | 
**woodcuttingXp** | **kotlin.Int** | The current xp level of the Woodcutting skill. | 
**woodcuttingMaxXp** | **kotlin.Int** | Woodcutting XP required to level up the skill. | 
**fishingLevel** | **kotlin.Int** | Fishing level. | 
**fishingXp** | **kotlin.Int** | The current xp level of the Fishing skill. | 
**fishingMaxXp** | **kotlin.Int** | Fishing XP required to level up the skill. | 
**weaponcraftingLevel** | **kotlin.Int** | Weaponcrafting level. | 
**weaponcraftingXp** | **kotlin.Int** | The current xp level of the Weaponcrafting skill. | 
**weaponcraftingMaxXp** | **kotlin.Int** | Weaponcrafting XP required to level up the skill. | 
**gearcraftingLevel** | **kotlin.Int** | Gearcrafting level. | 
**gearcraftingXp** | **kotlin.Int** | The current xp level of the Gearcrafting skill. | 
**gearcraftingMaxXp** | **kotlin.Int** | Gearcrafting XP required to level up the skill. | 
**jewelrycraftingLevel** | **kotlin.Int** | Jewelrycrafting level. | 
**jewelrycraftingXp** | **kotlin.Int** | The current xp level of the Jewelrycrafting skill. | 
**jewelrycraftingMaxXp** | **kotlin.Int** | Jewelrycrafting XP required to level up the skill. | 
**cookingLevel** | **kotlin.Int** | The current xp level of the Cooking skill. | 
**cookingXp** | **kotlin.Int** | Cooking XP. | 
**cookingMaxXp** | **kotlin.Int** | Cooking XP required to level up the skill. | 
**alchemyLevel** | **kotlin.Int** | Alchemy level. | 
**alchemyXp** | **kotlin.Int** | Alchemy XP. | 
**alchemyMaxXp** | **kotlin.Int** | Alchemy XP required to level up the skill. | 
**hp** | **kotlin.Int** | Character actual HP. | 
**maxHp** | **kotlin.Int** | Character max HP. | 
**haste** | **kotlin.Int** | *Increase speed attack (reduce fight cooldown) | 
**criticalStrike** | **kotlin.Int** | % Critical strike. Critical strikes adds 50% extra damage to an attack (1.5x). | 
**wisdom** | **kotlin.Int** | Wisdom increases the amount of XP gained from fights and skills (1% extra per 10 wisdom). | 
**prospecting** | **kotlin.Int** | Prospecting increases the chances of getting drops from fights and skills (1% extra per 10 PP). | 
**attackFire** | **kotlin.Int** | Fire attack. | 
**attackEarth** | **kotlin.Int** | Earth attack. | 
**attackWater** | **kotlin.Int** | Water attack. | 
**attackAir** | **kotlin.Int** | Air attack. | 
**dmg** | **kotlin.Int** | % Damage. Damage increases your attack in all elements. | 
**dmgFire** | **kotlin.Int** | % Fire damage. Damage increases your fire attack. | 
**dmgEarth** | **kotlin.Int** | % Earth damage. Damage increases your earth attack. | 
**dmgWater** | **kotlin.Int** | % Water damage. Damage increases your water attack. | 
**dmgAir** | **kotlin.Int** | % Air damage. Damage increases your air attack. | 
**resFire** | **kotlin.Int** | % Fire resistance. Reduces fire attack. | 
**resEarth** | **kotlin.Int** | % Earth resistance. Reduces earth attack. | 
**resWater** | **kotlin.Int** | % Water resistance. Reduces water attack. | 
**resAir** | **kotlin.Int** | % Air resistance. Reduces air attack. | 
**x** | **kotlin.Int** | Character x coordinate. | 
**y** | **kotlin.Int** | Character y coordinate. | 
**cooldown** | **kotlin.Int** | Cooldown in seconds. | 
**weaponSlot** | **kotlin.String** | Weapon slot. | 
**runeSlot** | **kotlin.String** | Rune slot. | 
**shieldSlot** | **kotlin.String** | Shield slot. | 
**helmetSlot** | **kotlin.String** | Helmet slot. | 
**bodyArmorSlot** | **kotlin.String** | Body armor slot. | 
**legArmorSlot** | **kotlin.String** | Leg armor slot. | 
**bootsSlot** | **kotlin.String** | Boots slot. | 
**ring1Slot** | **kotlin.String** | Ring 1 slot. | 
**ring2Slot** | **kotlin.String** | Ring 2 slot. | 
**amuletSlot** | **kotlin.String** | Amulet slot. | 
**artifact1Slot** | **kotlin.String** | Artifact 1 slot. | 
**artifact2Slot** | **kotlin.String** | Artifact 2 slot. | 
**artifact3Slot** | **kotlin.String** | Artifact 3 slot. | 
**utility1Slot** | **kotlin.String** | Utility 1 slot. | 
**utility1SlotQuantity** | **kotlin.Int** | Utility 1 quantity. | 
**utility2Slot** | **kotlin.String** | Utility 2 slot. | 
**utility2SlotQuantity** | **kotlin.Int** | Utility 2 quantity. | 
**bagSlot** | **kotlin.String** | Bag slot. | 
**task** | **kotlin.String** | Task in progress. | 
**taskType** | **kotlin.String** | Task type. | 
**taskProgress** | **kotlin.Int** | Task progression. | 
**taskTotal** | **kotlin.Int** | Task total objective. | 
**inventoryMaxItems** | **kotlin.Int** | Inventory max items. | 
**cooldownExpiration** | [**java.time.OffsetDateTime**](java.time.OffsetDateTime.md) | Datetime Cooldown expiration. |  [optional]
**inventory** | [**kotlin.collections.List&lt;InventorySlot&gt;**](InventorySlot.md) | List of inventory slots. |  [optional]



