
# MonsterSchema

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **kotlin.String** | Name of the monster. | 
**code** | **kotlin.String** | The code of the monster. This is the monster&#39;s unique identifier (ID). | 
**level** | **kotlin.Int** | Monster level. | 
**hp** | **kotlin.Int** | Monster hit points. | 
**attackFire** | **kotlin.Int** | Monster fire attack. | 
**attackEarth** | **kotlin.Int** | Monster earth attack. | 
**attackWater** | **kotlin.Int** | Monster water attack. | 
**attackAir** | **kotlin.Int** | Monster air attack. | 
**resFire** | **kotlin.Int** | Monster % fire resistance. | 
**resEarth** | **kotlin.Int** | Monster % earth resistance. | 
**resWater** | **kotlin.Int** | Monster % water resistance. | 
**resAir** | **kotlin.Int** | Monster % air resistance. | 
**criticalStrike** | **kotlin.Int** | Monster % critical strike. | 
**minGold** | **kotlin.Int** | Monster minimum gold drop.  | 
**maxGold** | **kotlin.Int** | Monster maximum gold drop.  | 
**drops** | [**kotlin.collections.List&lt;DropRateSchema&gt;**](DropRateSchema.md) | Monster drops. This is a list of items that the monster drops after killing the monster.  | 
**effects** | [**kotlin.collections.List&lt;SimpleEffectSchema&gt;**](SimpleEffectSchema.md) | List of effects. |  [optional]



