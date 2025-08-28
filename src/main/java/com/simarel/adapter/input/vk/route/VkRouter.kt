package com.simarel.adapter.input.vk.route

import com.simarel.port.input.VkCallbackEventProcessor
import com.simarel.usecase.vk.callback.VkCallbackEvent
import io.quarkus.arc.All
import io.quarkus.vertx.web.Route
import io.quarkus.vertx.web.RouteBase
import io.vertx.ext.web.RoutingContext

@RouteBase(path = "vk", produces = ["text/plain"])
open class VkRouter {
    val processors: Map<VkCallbackEvent, VkCallbackEventProcessor>

    constructor(
        @All processors: MutableList<VkCallbackEventProcessor>
    ) {
        this.processors = processors.associateBy { it.event() }
    }

    @Route(methods = [Route.HttpMethod.POST])
    fun callback(rc: RoutingContext) {
        val body = rc.body().asJsonObject()
        val type = VkCallbackEvent.mapOrUnknown(body.getString("type"))

        val processor = processors.get(type)!!
        processor.execute(body)
        rc.response().end(processor.response())
    }
}