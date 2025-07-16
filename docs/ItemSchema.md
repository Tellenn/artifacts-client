
# ItemSchema

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **kotlin.String** | Item name. | 
**code** | **kotlin.String** | Item code. This is the item&#39;s unique identifier (ID). | 
**level** | **kotlin.Int** | Item level. | 
**type** | **kotlin.String** | Item type. | 
**subtype** | **kotlin.String** | Item subtype. | 
**description** | **kotlin.String** | Item description. | 
**tradeable** | **kotlin.Boolean** | Item tradeable status. A non-tradeable item cannot be exchanged or sold. | 
**conditions** | [**kotlin.collections.List&lt;ConditionSchema&gt;**](ConditionSchema.md) | Item conditions. If applicable. Conditions for using or equipping the item. |  [optional]
**effects** | [**kotlin.collections.List&lt;SimpleEffectSchema&gt;**](SimpleEffectSchema.md) | List of object effects. For equipment, it will include item stats. |  [optional]
**craft** | [**ItemSchemaCraft**](ItemSchemaCraft.md) |  |  [optional]



