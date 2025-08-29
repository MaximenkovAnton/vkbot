package com.simarel.adapter.input.vk.router

import io.vertx.ext.web.RoutingContext

fun interface VkMediatorRouter {
    fun callback(rc: RoutingContext)
}