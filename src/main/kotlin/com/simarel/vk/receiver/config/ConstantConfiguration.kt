package com.simarel.vk.receiver.config

import com.simarel.vk.receiver.domain.vo.ConfirmationCode
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.inject.ConfigProperty

@Dependent
class ConstantConfiguration {

    @Produces
    @Singleton
    fun confirmationCode(@ConfigProperty(name = "vk.confirmation-code") secret: String) = ConfirmationCode(secret)

}