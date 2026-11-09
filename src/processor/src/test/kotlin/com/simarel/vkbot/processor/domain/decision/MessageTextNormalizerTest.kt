package com.simarel.vkbot.processor.domain.decision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private const val DOUBLE_SPACED_INPUT =
    "К  а  з  и  н  о     о  н  л  а  й  н  ,     б  о  н  у  с     5  0  0  %     н  а     " +
        "п  е  р  в  ы  й     д  е  п  о  з  и  т"
private const val YO_LEET_INPUT = "ё0ё"

private val REFERENCE_TABLE = listOf(
    "Заработок от 100 000 рублей в день, пиши в личку" to "Заработок от 100 000 рублей в день, пиши в личку",
    "Привет, как дела? Когда встречаемся?" to "Привет, как дела? Когда встречаемся?",
    "Смотрел вчера казино в фильме, занятный эпизод" to "Смотрел вчера казино в фильме, занятный эпизод",
    "/start" to "/start",
    "Зapaбotok ha kaзиho" to "Заработок ha казино",
    "ЗAработок" to "ЗАработок",
    "Bратья" to "Вратья",
    "ЗAРАБОТОК" to "ЗAРАБОТОК",
    "Купи п0дпис4ик0в и лайки дешев0, накрутка ВК" to "Купи подписчиков и лайки дешево, накрутка ВК",
    "x10 за неделю" to "x10 за неделю",
    "а0б" to "аоб",
    "К\u200bа\u200bз\u200bи\u200bн\u200bо\u200b \u200bо\u200bн\u200bл\u200bа\u200bй\u200bн\u200b," +
        "\u200b \u200bб\u200bо\u200bн\u200bу\u200bс\u200b \u200b5\u200b0\u200b0\u200b%\u200b \u200b" +
        "н\u200bа\u200b \u200bп\u200bе\u200bр\u200bв\u200bы\u200bй\u200b \u200bд\u200bе\u200bп\u200b" +
        "о\u200bз\u200bи\u200bт" to "Казино онлайн, бонус 500% на первый депозит",
    "за\u00adработок" to "заработок",
    "\uFF3Aаработок" to "Zаработок",
    "К а з и н о   о н л а й н ,   б о н у с   5 0 0 %   н а   п е р в ы й   д е п о з и т" to
        "Казино онлайн бонус 500% на первый депозит",
    "К.а.з.и.н.о. .о.н.л.а.й.н.,. .б.о.н.у.с. .5.0.0.%. .н.а. .п.е.р.в.ы.й. .д.е.п.о.з.и.т" to
        "Казино онлайн бонус 500% на первый депозит",
    DOUBLE_SPACED_INPUT to "К а з и н о о н л а й н б о н у с 5 0 0 % н а п е р в ы й д е п о з и т",
    "Ёлка ёжик" to "Елка ежик",
    "  Привет  мир  " to "Привет мир",
    "" to "",
    "   " to "",
    "\u200b" to "",
    "\u00a0\u3000" to "",
    YO_LEET_INPUT to "е0е",
    "Oпepa Kpиптa" to "Опера Крипта",
    "П0дписаться на канал" to "Подписаться на канал",
    "А0Б" to "АоБ",
)

private val ENUMERATED_RULES = listOf(
    "\u200c\u200d\u2060\ufeff\t\n" to "",
    "за\u200cработок" to "заработок",
    "а3б" to "азб",
    "а6б" to "абб",
    "0п" to "0п",
    "п0" to "п0",
    "а б в г д е ж" to "а б в г д е ж",
    "а б в г д е ж з" to "абвгдежз",
    "ab cd ef gh ij kl mn op" to "ab cd ef gh ij kl mn op",
    "аb" to "аь",
    "аB" to "аВ",
    "аa аc аe аx аy аm аh аA аC аE аX аY аM аH аT аP" to
        "аа ас ае ах ау ам ан аА аС аЕ аХ аУ аМ аН аТ аР",
    "за\tработок" to "за работок",
    "за\nработок" to "за работок",
    "за\u2028работок" to "за работок",
)

class MessageTextNormalizerTest {

    @Test
    fun `normalize matches the reference table`() {
        REFERENCE_TABLE.forEach { (input, expected) ->
            assertEquals(expected, MessageTextNormalizer.normalize(input), input)
        }
    }

    @Test
    fun `normalize covers every enumerated rule`() {
        ENUMERATED_RULES.forEach { (input, expected) ->
            assertEquals(expected, MessageTextNormalizer.normalize(input), input)
        }
    }

    @Test
    fun `normalize is idempotent for every table row except the double-spaced and the yo-leet ones`() {
        REFERENCE_TABLE
            .filterNot { it.first == DOUBLE_SPACED_INPUT || it.first == YO_LEET_INPUT }
            .forEach { (input, _) ->
                assertEquals(
                    MessageTextNormalizer.normalize(input),
                    MessageTextNormalizer.normalize(MessageTextNormalizer.normalize(input)),
                    input,
                )
            }
        assertEquals(
            "Казиноонлайнбонус500%напервыйдепозит",
            MessageTextNormalizer.normalize(MessageTextNormalizer.normalize(DOUBLE_SPACED_INPUT)),
        )
        assertEquals("еое", MessageTextNormalizer.normalize(MessageTextNormalizer.normalize(YO_LEET_INPUT)))
    }

    @Test
    fun `normalize keeps empty and whitespace-only text empty`() {
        listOf("", "   ", "\u200b\u200b", "\u00a0\u3000", "\u00ad").forEach { input ->
            assertEquals("", MessageTextNormalizer.normalize(input), input)
        }
    }

    @Test
    fun `normalize leaves clean text unchanged`() {
        listOf(
            "Привет, как дела? Когда встречаемся?",
            "Спасибо за вчерашнюю помощь, очень выручил",
            "Смотрел вчера казино в фильме, занятный эпизод",
            "/start",
        ).forEach { input ->
            assertEquals(input, MessageTextNormalizer.normalize(input), input)
        }
    }
}
