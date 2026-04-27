package com.simarel.vkbot.persistence.port.output.persistence

interface UserProfileRepositoryPort :
    SaveUserProfilePort,
    FindUserProfilesByIdsPort,
    FilterExistingUserIdsPort
