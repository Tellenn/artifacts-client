
# StatusSchema

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**version** | **kotlin.String** | Game version. | 
**serverTime** | [**java.time.OffsetDateTime**](java.time.OffsetDateTime.md) | Server time. | 
**maxLevel** | **kotlin.Int** | Maximum level. | 
**maxSkillLevel** | **kotlin.Int** | Maximum skill level. | 
**charactersOnline** | **kotlin.Int** | Characters online. | 
**announcements** | [**kotlin.collections.List&lt;AnnouncementSchema&gt;**](AnnouncementSchema.md) | Server announcements. | 
**rateLimits** | [**kotlin.collections.List&lt;RateLimitSchema&gt;**](RateLimitSchema.md) | Rate limits. | 
**season** | [**SeasonSchema**](SeasonSchema.md) | Current season details. |  [optional]



