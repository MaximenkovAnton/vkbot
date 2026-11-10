package com.simarel.vkbot.vkFacade.adapter.output.vk.circuitbreaker

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.vkFacade.adapter.output.vk.dto.VkUserDto

object VkUserProfileMapper {

    fun map(userDto: VkUserDto): VkUserProfile {
        val fields = JsonNodeFactory.instance.objectNode()
        userDto.extraFields.forEach { (name, value) -> fields.set<JsonNode>(name, value) }
        return VkUserProfile.of(
            id = userDto.id,
            firstName = userDto.firstName,
            lastName = userDto.lastName,
            screenName = userDto.screenName,
            birthDate = userDto.birthDate,
            city = userDto.city?.title,
            details = details(fields),
        )
    }

    private fun details(fields: JsonNode): Map<String, Any?> {
        val details = LinkedHashMap<String, Any?>()
        val personal = jsonObjectOrNull(fields, PERSONAL)
        val occupation = jsonObjectOrNull(fields, OCCUPATION)
        put(details, SEX, labelOrNull(intOrNull(fields, SEX), SEX_LABELS))
        put(details, RELATION, labelOrNull(intOrNull(fields, RELATION), RELATION_LABELS))
        put(details, POLITICAL, labelOrNull(intOrNull(personal, POLITICAL), POLITICAL_LABELS))
        put(details, PEOPLE_MAIN, labelOrNull(intOrNull(personal, PEOPLE_MAIN), PEOPLE_MAIN_LABELS))
        put(details, LIFE_MAIN, labelOrNull(intOrNull(personal, LIFE_MAIN), LIFE_MAIN_LABELS))
        put(details, SMOKING, labelOrNull(intOrNull(personal, SMOKING), ATTITUDE_LABELS))
        put(details, ALCOHOL, labelOrNull(intOrNull(personal, ALCOHOL), ATTITUDE_LABELS))
        put(details, OCCUPATION_TYPE, textOrNull(occupation, TYPE)?.let(OCCUPATION_TYPES::get))
        put(details, OCCUPATION_NAME, textOrNull(occupation, NAME))
        put(details, COUNTRY, textOrNull(jsonObjectOrNull(fields, COUNTRY), TITLE))
        put(details, TIMEZONE, intOrNull(fields, TIMEZONE)?.let { "UTC" + if (it < 0) it.toString() else "+$it" })
        put(details, FOLLOWERS_COUNT, intOrNull(fields, FOLLOWERS_COUNT))
        put(details, VERIFIED, boolOrNull(fields, VERIFIED))
        put(
            details,
            COUNTERS,
            jsonObjectOrNull(fields, COUNTERS)?.let { node ->
                val counters = LinkedHashMap<String, Any?>()
                node.fieldNames().forEach { name ->
                    val value = node.get(name)
                    if (value.isNumber) {
                        counters[name] = if (value.isIntegralNumber) value.asInt() else value.asDouble()
                    }
                }
                counters
            },
        )
        put(
            details,
            CONNECTIONS,
            jsonObjectOrNull(fields, CONNECTIONS)?.let { node ->
                val connections = LinkedHashMap<String, Any?>()
                CONNECTION_NAMES.forEach { name -> put(connections, name, textOrNull(node, name)) }
                connections
            },
        )
        TOP_LEVEL_TEXTS.forEach { name -> put(details, name, textOrNull(fields, name)) }
        personalDetails(details, personal, fields)
        put(details, CAREER, jsonArrayOrNull(fields, CAREER)?.mapNotNull { node -> careerItem(node) })
        put(details, MILITARY, jsonArrayOrNull(fields, MILITARY)?.mapNotNull { node -> militaryItem(node) })
        put(details, UNIVERSITIES, jsonArrayOrNull(fields, UNIVERSITIES)?.mapNotNull { node -> universityItem(node) })
        put(details, SCHOOLS, jsonArrayOrNull(fields, SCHOOLS)?.mapNotNull { node -> schoolItem(node) })
        put(details, RELATIVES, jsonArrayOrNull(fields, RELATIVES)?.mapNotNull { node -> relativeItem(node) })
        return details
    }

    private fun personalDetails(
        details: MutableMap<String, Any?>,
        personal: JsonNode?,
        fields: JsonNode,
    ) {
        val contacts = jsonObjectOrNull(fields, CONTACTS)
        val education = jsonObjectOrNull(fields, EDUCATION)
        put(details, RELIGION, textOrNull(personal, RELIGION))
        put(details, INSPIRED_BY, textOrNull(personal, INSPIRED_BY))
        put(
            details,
            LANGUAGES,
            jsonArrayOrNull(personal, LANGS)?.mapNotNull { element ->
                element.takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() }
            },
        )
        put(details, MOBILE_PHONE, textOrNull(contacts, MOBILE_PHONE))
        put(details, HOME_PHONE, textOrNull(contacts, HOME_PHONE))
        put(
            details,
            EDUCATION_UNIVERSITY,
            textOrNull(education, UNIVERSITY_NAME) ?: textOrNull(fields, UNIVERSITY_NAME),
        )
        put(details, EDUCATION_FACULTY, textOrNull(education, FACULTY_NAME) ?: textOrNull(fields, FACULTY_NAME))
        put(details, EDUCATION_GRADUATION, intOrNull(education, GRADUATION) ?: intOrNull(fields, GRADUATION))
        put(details, EDUCATION_FORM, textOrNull(fields, EDUCATION_FORM))
        put(details, EDUCATION_STATUS, textOrNull(fields, EDUCATION_STATUS))
    }

    private fun careerItem(node: JsonNode): Map<String, Any?>? {
        val item = LinkedHashMap<String, Any?>()
        put(item, COMPANY, textOrNull(node, COMPANY))
        put(item, POSITION, textOrNull(node, POSITION))
        put(item, CITY, textOrNull(node, CITY_NAME))
        put(item, FROM_YEAR, intOrNull(node, FROM))
        put(item, UNTIL_YEAR, intOrNull(node, UNTIL))
        return item.takeIf { it.containsKey(COMPANY) || it.containsKey(POSITION) || it.containsKey(CITY) }
    }

    private fun militaryItem(node: JsonNode): Map<String, Any?>? {
        val unit = textOrNull(node, UNIT) ?: return null
        val item = linkedMapOf<String, Any?>(UNIT to unit)
        put(item, FROM_YEAR, intOrNull(node, FROM))
        put(item, UNTIL_YEAR, intOrNull(node, UNTIL))
        return item
    }

    private fun universityItem(node: JsonNode): Map<String, Any?>? {
        val name = textOrNull(node, NAME) ?: return null
        val item = linkedMapOf<String, Any?>(NAME to name)
        put(item, FACULTY, textOrNull(node, FACULTY_NAME))
        put(item, CHAIR, textOrNull(node, CHAIR_NAME))
        put(item, GRADUATION_YEAR, intOrNull(node, GRADUATION))
        put(item, EDUCATION_FORM, textOrNull(node, EDUCATION_FORM))
        put(item, EDUCATION_STATUS, textOrNull(node, EDUCATION_STATUS))
        return item
    }

    private fun schoolItem(node: JsonNode): Map<String, Any?>? {
        val name = textOrNull(node, NAME) ?: return null
        val item = linkedMapOf<String, Any?>(NAME to name)
        put(item, TYPE, labelOrNull(intOrNull(node, TYPE), SCHOOL_TYPE_LABELS))
        put(item, YEAR_FROM, intOrNull(node, YEAR_FROM))
        put(item, YEAR_TO, intOrNull(node, YEAR_TO))
        put(item, CLASS, textOrNull(node, CLASS))
        put(item, SPECIALITY, textOrNull(node, SPECIALITY))
        return item
    }

    private fun relativeItem(node: JsonNode): Map<String, Any?>? {
        val name = listOfNotNull(textOrNull(node, FIRST_NAME), textOrNull(node, LAST_NAME))
            .joinToString(SEPARATOR)
            .takeIf { it.isNotEmpty() } ?: return null
        val item = linkedMapOf<String, Any?>(NAME to name)
        put(item, TYPE, textOrNull(node, TYPE)?.let(RELATIVE_TYPES::get))
        return item
    }

    private fun put(details: MutableMap<String, Any?>, name: String, value: Any?) {
        if (value != null) {
            details[name] = value
        }
    }

    private const val SEPARATOR = " "
    private const val PERSONAL = "personal"
    private const val OCCUPATION = "occupation"
    private const val CONTACTS = "contacts"
    private const val EDUCATION = "education"
    private const val LANGS = "langs"
    private const val SEX = "sex"
    private const val RELATION = "relation"
    private const val POLITICAL = "political"
    private const val PEOPLE_MAIN = "people_main"
    private const val LIFE_MAIN = "life_main"
    private const val SMOKING = "smoking"
    private const val ALCOHOL = "alcohol"
    private const val OCCUPATION_TYPE = "occupation_type"
    private const val OCCUPATION_NAME = "occupation_name"
    private const val COUNTRY = "country"
    private const val TITLE = "title"
    private const val TIMEZONE = "timezone"
    private const val FOLLOWERS_COUNT = "followers_count"
    private const val VERIFIED = "verified"
    private const val COUNTERS = "counters"
    private const val CONNECTIONS = "connections"
    private const val RELIGION = "religion"
    private const val INSPIRED_BY = "inspired_by"
    private const val LANGUAGES = "languages"
    private const val MOBILE_PHONE = "mobile_phone"
    private const val HOME_PHONE = "home_phone"
    private const val UNIVERSITY_NAME = "university_name"
    private const val FACULTY_NAME = "faculty_name"
    private const val GRADUATION = "graduation"
    private const val EDUCATION_UNIVERSITY = "education_university"
    private const val EDUCATION_FACULTY = "education_faculty"
    private const val EDUCATION_GRADUATION = "education_graduation"
    private const val EDUCATION_FORM = "education_form"
    private const val EDUCATION_STATUS = "education_status"
    private const val CAREER = "career"
    private const val MILITARY = "military"
    private const val UNIVERSITIES = "universities"
    private const val SCHOOLS = "schools"
    private const val RELATIVES = "relatives"
    private const val COMPANY = "company"
    private const val POSITION = "position"
    private const val CITY = "city"
    private const val CITY_NAME = "city_name"
    private const val FROM = "from"
    private const val UNTIL = "until"
    private const val FROM_YEAR = "from_year"
    private const val UNTIL_YEAR = "until_year"
    private const val UNIT = "unit"
    private const val NAME = "name"
    private const val FACULTY = "faculty"
    private const val CHAIR = "chair"
    private const val CHAIR_NAME = "chair_name"
    private const val GRADUATION_YEAR = "graduation_year"
    private const val TYPE = "type"
    private const val YEAR_FROM = "year_from"
    private const val YEAR_TO = "year_to"
    private const val CLASS = "class"
    private const val SPECIALITY = "speciality"
    private const val FIRST_NAME = "first_name"
    private const val LAST_NAME = "last_name"

    private val TOP_LEVEL_TEXTS = listOf(
        "home_town",
        "maiden_name",
        "nickname",
        "domain",
        "site",
        "status",
        "about",
        "activities",
        "interests",
        "music",
        "movies",
        "tv",
        "books",
        "games",
        "quotes",
    )
    private val CONNECTION_NAMES = listOf("skype", "instagram", "twitter", "facebook", "livejournal")
    private val OCCUPATION_TYPES = mapOf("work" to "Работа", "school" to "Школа", "university" to "Вуз")
    private val RELATIVE_TYPES = mapOf(
        "parent" to "Родитель",
        "child" to "Ребёнок",
        "grandparent" to "Бабушка/дедушка",
        "grandchild" to "Внук/внучка",
        "sibling" to "Брат/сестра",
    )

    private fun labels(startAt: Int, vararg names: String): Map<Int, String> = names
        .mapIndexed { index, name -> (startAt + index) to name }
        .toMap()

    private val SEX_LABELS = labels(1, "Женский", "Мужской")
    private val RELATION_LABELS = labels(
        1,
        "Свободен(на)",
        "В отношениях",
        "Помолвлен(а)",
        "В браке",
        "Всё сложно",
        "В активном поиске",
        "Влюблён(а)",
        "В гражданском браке",
    )
    private val POLITICAL_LABELS = labels(
        1,
        "Коммунистические",
        "Социалистические",
        "Умеренные",
        "Либеральные",
        "Консервативные",
        "Монархические",
        "Ультраконсервативные",
        "Апатичные",
        "Либертарианские",
    )
    private val PEOPLE_MAIN_LABELS = labels(
        1,
        "Интеллект и творчество",
        "Красота и искусство",
        "Доброта",
        "Власть и богатство",
        "Смелость и упорство",
        "Юмор и жизнелюбие",
    )
    private val LIFE_MAIN_LABELS = labels(
        1,
        "Семья и дети",
        "Карьера и деньги",
        "Развлечения и отдых",
        "Наука и исследования",
        "Совершенствование мира",
        "Самосовершенствование",
        "Красота и искусство",
        "Слава и влияние",
    )
    private val ATTITUDE_LABELS = labels(
        1,
        "Отрицательно",
        "Нейтрально",
        "Положительно",
        "Компромиссно",
    )
    private val SCHOOL_TYPE_LABELS = labels(
        0,
        "Школа",
        "Гимназия",
        "Лицей",
        "Школа-интернат",
        "Школа-интернат",
        "Кадетская школа",
        "Суворовское училище",
        "Нахимовское училище",
        "Кадетский корпус",
    )
}
