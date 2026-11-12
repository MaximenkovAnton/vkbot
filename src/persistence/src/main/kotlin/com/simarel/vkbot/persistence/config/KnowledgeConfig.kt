package com.simarel.vkbot.persistence.config

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class KnowledgeConfig(
    @ConfigProperty(name = "knowledge.confirm-threshold", defaultValue = "0.8")
    val confirmThreshold: Double,
    @ConfigProperty(name = "knowledge.evidence-refs-limit", defaultValue = "20")
    val evidenceRefsLimit: Int,
)
