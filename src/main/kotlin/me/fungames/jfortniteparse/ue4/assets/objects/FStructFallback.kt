package me.fungames.jfortniteparse.ue4.assets.objects

import me.fungames.jfortniteparse.exceptions.MissingSchemaException
import me.fungames.jfortniteparse.exceptions.ParserException
import me.fungames.jfortniteparse.ue4.UClass
import me.fungames.jfortniteparse.ue4.assets.exports.UScriptStruct
import me.fungames.jfortniteparse.ue4.assets.exports.UStruct
import me.fungames.jfortniteparse.ue4.assets.exports.deserializeVersionedTaggedProperties
import me.fungames.jfortniteparse.ue4.assets.reader.FAssetArchive
import me.fungames.jfortniteparse.ue4.assets.writer.FAssetArchiveWriter
import me.fungames.jfortniteparse.ue4.objects.uobject.EPackageFlags
import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.objects.uobject.serialization.deserializeUnversionedProperties

class FStructFallback : UClass, IPropertyHolder {
    override var properties: MutableList<FPropertyTag>

    constructor(Ar: FAssetArchive, struct: Lazy<out UStruct>?, structName: FName = FName.NAME_None) {
        super.init(Ar)
        properties = mutableListOf()
        if (Ar.useUnversionedPropertySerialization) {
            val structClass = struct?.value
                ?: throw ParserException("Unknown struct type $structName, can't proceed with serialization", Ar)
            deserializeUnversionedProperties(properties, structClass, Ar)
        } else {
            deserializeVersionedTaggedProperties(properties, Ar)
        }
        super.complete(Ar)
    }

    constructor(Ar: FAssetArchive, structName: FName) : this(Ar, Ar.provider?.let {
        lazy {
            var struct = Ar.provider.mappingsProvider.getStruct(structName)
            if (struct == null) {
                if (Ar.owner.packageFlags and EPackageFlags.PKG_UnversionedProperties.value != 0) {
                    throw MissingSchemaException("Unknown struct $structName")
                }
                struct = UScriptStruct(structName)
            }
            struct
        }
    }, structName)

    fun serialize(Ar: FAssetArchiveWriter) {
        super.initWrite(Ar)
        properties.forEach {
            it.serialize(Ar, true)
        }
        Ar.writeFName(FName.getByNameMap("None", Ar.nameMap) ?: throw ParserException("NameMap must contain \"None\""))
        super.completeWrite(Ar)
    }

    inline fun <reified T> set(name: String, value: T) {
        if (getOrNull<T>(name) != null)
            properties.first { it.name.text == name }.setTagTypeValue(value)
    }

    inline fun <reified T> getOrDefault(name: String, default: T): T {
        val value: T? = getOrNull(name)
        return value ?: default
    }

    fun <T> getOrNull(name: String, clazz: Class<T>): T? = properties.firstOrNull { it.name.text == name }?.getTagTypeValue(clazz)

    inline fun <reified T> getOrNull(name: String) = getOrNull(name, T::class.java)

    inline fun <reified T> get(name: String): T = getOrNull(name) ?: throw KotlinNullPointerException("$name must be not-null")

    constructor(properties: MutableList<FPropertyTag>) {
        this.properties = properties
    }
}