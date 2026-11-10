package com.simarel.vkbot.vkFacade.adapter.output.vk.circuitbreaker

import com.simarel.vkbot.testfixtures.domain.FakeVkDtoProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class VkUserProfileMapperTest {

    @Test
    fun `maps enum codes to russian labels`() {
        val details = mappedDetails(
            """{"sex":2,"relation":4,
            "personal":{"political":3,"people_main":3,"life_main":1,"smoking":1,"alcohol":2},
            "occupation":{"type":"work"},
            "relatives":[{"type":"parent","first_name":"Мария","last_name":"Петрова"}]}""",
        )

        assertEquals("Мужской", details["sex"])
        assertEquals("В браке", details["relation"])
        assertEquals("Умеренные", details["political"])
        assertEquals("Доброта", details["people_main"])
        assertEquals("Семья и дети", details["life_main"])
        assertEquals("Отрицательно", details["smoking"])
        assertEquals("Нейтрально", details["alcohol"])
        assertEquals("Работа", details["occupation_type"])
        assertEquals(listOf(mapOf("name" to "Мария Петрова", "type" to "Родитель")), details["relatives"])
    }

    @Test
    fun `drops unknown codes and zero sex and relation`() {
        val details = mappedDetails(
            """{"sex":0,"relation":0,"personal":{"political":99},"occupation":{"type":"army"},
            "schools":[{"name":"Школа","type":99}],
            "relatives":[{"type":"cousin","first_name":"Иван"}]}""",
        )

        assertFalse(details.containsKey("sex"))
        assertFalse(details.containsKey("relation"))
        assertFalse(details.containsKey("political"))
        assertFalse(details.containsKey("occupation_type"))
        val school = (details.getValue("schools") as List<*>).single() as Map<*, *>
        assertEquals("Школа", school["name"])
        assertFalse(school.containsKey("type"))
        val relative = (details.getValue("relatives") as List<*>).single() as Map<*, *>
        assertEquals("Иван", relative["name"])
        assertFalse(relative.containsKey("type"))
    }

    @Test
    fun `maps nested objects to human values without vk ids`() {
        val details = mappedDetails(
            """{"country":{"id":1,"title":"Россия"},
            "career":[{"company":"Acme","position":"Engineer","city_id":1,"city_name":"Витебск",
            "from":2015,"until":2020,"group_id":9}],
            "universities":[{"name":"ВГУ","faculty_name":"ФПМ","chair_name":"Кафедра ИТ","graduation":2012,
            "education_form":"Очная","education_status":"Выпускник","id":5}],
            "schools":[{"name":"Школа №5","type":2,"year_from":2000,"year_to":2010,"class":"11А",
            "speciality":"Математика","id":6}],
            "military":[{"unit":"В/ч 12345","from":2011,"until":2012,"id":7}],
            "relatives":[{"id":3,"type":"parent","first_name":"Мария","last_name":"Петрова"}]}""",
        )

        assertEquals("Россия", details["country"])
        assertEquals(
            listOf(
                mapOf(
                    "company" to "Acme",
                    "position" to "Engineer",
                    "city" to "Витебск",
                    "from_year" to 2015,
                    "until_year" to 2020,
                ),
            ),
            details["career"],
        )
        assertEquals(
            listOf(
                mapOf(
                    "name" to "ВГУ",
                    "faculty" to "ФПМ",
                    "chair" to "Кафедра ИТ",
                    "graduation_year" to 2012,
                    "education_form" to "Очная",
                    "education_status" to "Выпускник",
                ),
            ),
            details["universities"],
        )
        assertEquals(
            listOf(
                mapOf(
                    "name" to "Школа №5",
                    "type" to "Лицей",
                    "year_from" to 2000,
                    "year_to" to 2010,
                    "class" to "11А",
                    "speciality" to "Математика",
                ),
            ),
            details["schools"],
        )
        assertEquals(
            listOf(mapOf("unit" to "В/ч 12345", "from_year" to 2011, "until_year" to 2012)),
            details["military"],
        )
        assertEquals(listOf(mapOf("name" to "Мария Петрова", "type" to "Родитель")), details["relatives"])
        listOf("group_id", "city_id", "from", "until").forEach { assertFalse(details.containsKey(it), it) }
    }

    @Test
    fun `skips blank strings volatile fields and keeps empty collections`() {
        val details = mappedDetails(
            """{"about":"","status":"   ","last_seen":{"time":1},"online":1,"photo_200":"http://photo",
            "has_photo":1,"friend_status":3,"wall_comments":1,"can_post":1,
            "personal":{"langs":[]},"career":[],"connections":{}}""",
        )

        listOf("about", "status", "last_seen", "online", "photo_200", "has_photo", "friend_status").forEach {
            assertFalse(details.containsKey(it), it)
        }
        assertFalse(details.containsKey("wall_comments"))
        assertFalse(details.containsKey("can_post"))
        assertEquals(emptyList<String>(), details["languages"])
        assertEquals(emptyList<String>(), details["career"])
        assertEquals(emptyMap<String, String>(), details["connections"])
    }

    @Test
    fun `maps personal education contacts and timezone`() {
        val details = mappedDetails(
            """{"personal":{"religion":"Православие","inspired_by":"Толстой","langs":["Русский","English"]},
            "contacts":{"mobile_phone":"+375291112233","home_phone":"+375172223344"},
            "education":{"university_name":"ВГУ","faculty_name":"ФПМ","graduation":2012},
            "education_form":"Очная","education_status":"Выпускник","timezone":3,"followers_count":42,
            "verified":1,"counters":{"friends":10,"followers":20},"connections":{"skype":"ivan.skype"}}""",
        )

        assertEquals("Православие", details["religion"])
        assertEquals("Толстой", details["inspired_by"])
        assertEquals(listOf("Русский", "English"), details["languages"])
        assertEquals("+375291112233", details["mobile_phone"])
        assertEquals("+375172223344", details["home_phone"])
        assertEquals("ВГУ", details["education_university"])
        assertEquals("ФПМ", details["education_faculty"])
        assertEquals(2012, details["education_graduation"])
        assertEquals("Очная", details["education_form"])
        assertEquals("Выпускник", details["education_status"])
        assertEquals(42, details["followers_count"])
        assertEquals(true, details["verified"])
        assertEquals(mapOf("friends" to 10, "followers" to 20), details["counters"])
        assertEquals(mapOf("skype" to "ivan.skype"), details["connections"])
        listOf(3 to "UTC+3", -5 to "UTC-5", 0 to "UTC+0").forEach { (hours, expected) ->
            assertEquals(expected, mappedDetails("""{"timezone":$hours}""")["timezone"])
        }
    }

    private fun mappedDetails(extraFieldsJson: String): Map<String, Any?> {
        val userDto = FakeVkDtoProvider.createVkUserDto(id = 1, extraFieldsJson = extraFieldsJson)
        return VkUserProfileMapper.map(userDto).details
    }
}
