
# EventSchema

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **kotlin.String** | Name of the event. | 
**code** | **kotlin.String** | Code of the event. | 
**content** | [**EventContentSchema**](EventContentSchema.md) | Content of the event. | 
**maps** | [**kotlin.collections.List&lt;EventMapSchema&gt;**](EventMapSchema.md) | Map list of the event. | 
**duration** | **kotlin.Int** | Duration in minutes. | 
**rate** | **kotlin.Int** | Rate spawn of the event. (1/rate every minute) | 



