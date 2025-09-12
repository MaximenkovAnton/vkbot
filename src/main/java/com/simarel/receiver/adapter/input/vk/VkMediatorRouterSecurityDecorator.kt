package com.simarel.receiver.adapter.input.vk

import com.simarel.receiver.domain.exception.AccessDeniedException
import io.quarkus.logging.Log
import jakarta.decorator.Decorator
import jakarta.decorator.Delegate
import jakarta.inject.Inject
import jakarta.json.JsonObject
import org.eclipse.microprofile.config.inject.ConfigProperty

@Decorator
class VkMediatorRouterSecurityDecorator(): VkMediatorRouter {
    @Inject
    @Delegate
    lateinit var delegate: VkMediatorRouter

    @ConfigProperty(name = "vk.secret")
    lateinit var secret: String

    override fun callback(event: JsonObject): String {
        val secret = event.getString("secret")
        if(secret == this.secret) {
            return delegate.callback(event)
        } else {
            Log.error {"Vk Router message without secret! $event}"}
            throw AccessDeniedException("Secret code not provided or incorrect")
        }
    }
}