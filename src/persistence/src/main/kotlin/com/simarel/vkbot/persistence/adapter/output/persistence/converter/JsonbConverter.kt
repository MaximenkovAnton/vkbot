package com.simarel.vkbot.persistence.adapter.output.persistence.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.postgresql.util.PGobject

@Converter(autoApply = true)
open class JsonbConverter : AttributeConverter<String?, PGobject?> {

    override fun convertToDatabaseColumn(attribute: String?): PGobject? {
        if (attribute == null) return null
        return PGobject().apply {
            type = "jsonb"
            value = attribute
        }
    }

    override fun convertToEntityAttribute(dbData: PGobject?): String? {
        return dbData?.value
    }
}
