package com.simarel.vkbot.greetings.usecase

import com.simarel.vkbot.greetings.command.collect.CollectGreetingPlansCommand
import com.simarel.vkbot.greetings.command.text.GenerateGreetingTextCommand
import com.simarel.vkbot.greetings.port.input.RunDailyGreetingsInputPort
import com.simarel.vkbot.share.port.output.persistence.ClaimDailyGreetingPort
import com.simarel.vkbot.share.port.output.vk.SendGreetingMessagePort
import jakarta.enterprise.context.ApplicationScoped
import java.lang.System.Logger.Level

@Suppress("TooGenericExceptionCaught")
@ApplicationScoped
open class RunDailyGreetingsUsecase(
    private val collectGreetingPlans: CollectGreetingPlansCommand,
    private val generateGreetingText: GenerateGreetingTextCommand,
    private val claimDailyGreeting: ClaimDailyGreetingPort,
    private val sendGreetingMessage: SendGreetingMessagePort,
) : RunDailyGreetingsInputPort {
    private val logger = System.getLogger(RunDailyGreetingsUsecase::class.java.name)

    override fun execute(request: RunDailyGreetingsInputPort.Request): RunDailyGreetingsInputPort.Response {
        val plans = collectGreetingPlans.execute(CollectGreetingPlansCommand.Request(request.date)).plans
        var sent = 0
        var failed = 0

        plans.groupBy { it.peerId }.forEach { (peerId, peerPlans) ->
            val claimed = try {
                claimDailyGreeting.execute(ClaimDailyGreetingPort.Request(request.date, peerId)).claimed
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            } catch (e: Exception) {
                failed += peerPlans.size
                logger.log(
                    Level.ERROR,
                    "Greeting claim failed for peerId=${peerId.value} (${e.javaClass.name})",
                    e,
                )
                return@forEach
            }
            if (!claimed) {
                logger.log(Level.INFO, "Greeting is already claimed: peerId=${peerId.value}")
                return@forEach
            }
            peerPlans.forEach { plan ->
                try {
                    val text = generateGreetingText.execute(
                        GenerateGreetingTextCommand.Request(request.date, plan),
                    ).text
                    sendGreetingMessage.execute(SendGreetingMessagePort.Request(peerId, text))
                    sent++
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                } catch (e: Exception) {
                    failed++
                    logger.log(
                        Level.ERROR,
                        "Greeting failed for peerId=${peerId.value} (${e.javaClass.name})",
                        e,
                    )
                }
            }
        }

        return RunDailyGreetingsInputPort.Response(planned = plans.size, sent = sent, failed = failed)
    }
}
