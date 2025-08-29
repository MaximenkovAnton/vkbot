package com.simarel.adapter.input.vk.router

import com.simarel.adapter.input.vk.processor.callback.VkCallbackEventProcessor
import com.simarel.adapter.input.vk.processor.callback.VkCallbackEvent
import io.quarkus.arc.All
import io.quarkus.vertx.web.Route
import io.quarkus.vertx.web.RouteBase
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Singleton

@RouteBase(path = "vk", produces = ["text/plain"])
@ApplicationScoped
open class VkMediatorRouterImpl(@All processors: MutableList<VkCallbackEventProcessor>): VkMediatorRouter {
    val processors: Map<VkCallbackEvent, VkCallbackEventProcessor> = processors.associateBy { it.event() }

    @Route(methods = [Route.HttpMethod.POST])
    override fun callback(rc: RoutingContext) {
        val body = rc.body().asJsonObject()
        val type = VkCallbackEvent.Companion.mapOrUnknown(body.getString("type"))

        val processor = processors.get(type)!!
        val response = processor.execute(body)
        rc.response().end(response)
    }
}