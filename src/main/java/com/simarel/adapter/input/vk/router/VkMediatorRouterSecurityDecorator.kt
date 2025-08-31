package com.simarel.adapter.input.vk.router

import com.simarel.domain.exception.AccessDeniedException
import io.quarkus.logging.Log
import io.vertx.ext.web.RoutingContext
import jakarta.decorator.Decorator
import jakarta.decorator.Delegate
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

@Decorator
class VkMediatorRouterSecurityDecorator(): VkMediatorRouter {
    @Inject
    @Delegate
    lateinit var delegate: VkMediatorRouter

    @ConfigProperty(name = "vk.secret")
    lateinit var secret: String

    override fun callback(rc: RoutingContext) {
        val secret = rc.body().asJsonObject().getString("secret")
        if(secret == this.secret) {
            return delegate.callback(rc)
        } else {
            Log.error {"Vk Router message without secret! ${rc.body()}"}
            throw AccessDeniedException("Secret code not provided or incorrect")
        }
    }
}