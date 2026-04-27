package com.simarel.vkbot.persistence.port.output.persistence

interface GroupProfileRepositoryPort :
    SaveGroupProfilePort,
    FindGroupProfilesByIdsPort,
    FilterExistingGroupIdsPort
