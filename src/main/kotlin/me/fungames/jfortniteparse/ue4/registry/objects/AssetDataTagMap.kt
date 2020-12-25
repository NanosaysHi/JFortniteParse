package me.fungames.jfortniteparse.ue4.registry.objects

import me.fungames.jfortniteparse.ue4.objects.uobject.FName
import me.fungames.jfortniteparse.ue4.objects.uobject.FNameEntryId
import me.fungames.jfortniteparse.ue4.reader.FArchive
import me.fungames.jfortniteparse.util.get

/**
 * Helper class for condensing strings of these types into  1 - 3 FNames
 * * [class]'[package].[object]'
 * * [package].[object]
 * * [package]
 */
class FAssetRegistryExportPath(val `class`: FName, val `object`: FName, val `package`: FName) {
    constructor(Ar: FArchive) : this(Ar.readFName(), Ar.readFName(), Ar.readFName())

    override fun toString(): String {
        val path = StringBuilder()
        toString(path)
        return path.toString()
    }

    fun toName(): FName {
        if (`class`.isNone() && `object`.isNone()) {
            return `package`
        }

        val path = StringBuilder()
        toString(path)
        return FName.dummy(path.toString())
    }

    fun toString(out: StringBuilder) {
        if (!`class`.isNone()) {
            out.append(`class`).append('\'')
        }
        out.append(`package`)
        if (!`object`.isNone()) {
            out.append('.').append(`object`)
        }
        if (!`class`.isNone()) {
            out.append('\'')
        }
    }

    fun isEmpty() = `class`.isNone() && `package`.isNone() && `object`.isNone()
}

/** Compact FAssetRegistryExportPath equivalent for when all FNames are numberless */
class FNumberlessExportPath(val `class`: FNameEntryId, val `object`: FNameEntryId, val `package`: FNameEntryId, val names: List<String>) {
    constructor(Ar: FArchive, names: List<String>) : this(FNameEntryId(Ar), FNameEntryId(Ar), FNameEntryId(Ar), names)

    fun makeNumberedPath() = FAssetRegistryExportPath(
        FName.dummy(names[`class`.value]),
        FName.dummy(names[`object`.value]),
        FName.dummy(names[`package`.value])
    )

    override fun toString() = makeNumberedPath().toString()
    fun toName() = makeNumberedPath().toName()
    fun toString(out: StringBuilder) = makeNumberedPath().toString(out)
}

enum class EValueType {
    AnsiString,
    WideString,
    NumberlessName,
    Name,
    NumberlessExportPath,
    ExportPath,
    LocalizedText
}

class FValueId {
    companion object {
        val TYPE_BITS = 3
        val INDEX_BITS = 32 - TYPE_BITS
    }

    val type: EValueType
    val index: UInt

    constructor(type: EValueType, index: UInt) {
        this.type = type
        this.index = index
    }

    constructor(Ar: FArchive) : this(Ar.readUInt32())

    constructor(int: UInt) {
        type = EValueType.values()[(int shl INDEX_BITS) shr INDEX_BITS]
        index = int shr TYPE_BITS
    }

    fun toInt() = type.ordinal.toUInt() or (index shl TYPE_BITS)
}

class FNumberedPair(val key: FName, val value: FValueId) {
    constructor(Ar: FArchive) : this(Ar.readFName(), FValueId(Ar))
}

class FNumberlessPair(val key: FNameEntryId, val value: FValueId) {
    constructor(Ar: FArchive) : this(FNameEntryId(Ar), FValueId(Ar))
}

/** Handle to a tag value owned by a managed FStore */
class FValueHandle(val store: FStore, val id: FValueId) {
    fun asString(): String {
        val index = id.index
        return when (id.type) {
            EValueType.AnsiString -> store.getAnsiString(index)
            EValueType.WideString -> store.getWideString(index)
            EValueType.NumberlessName -> store.nameMap[store.numberlessNames[index].value]
            EValueType.Name -> store.names[index].text
            EValueType.NumberlessExportPath -> store.numberlessExportPaths[index].toString()
            EValueType.ExportPath -> store.exportPaths[index].toString()
            EValueType.LocalizedText -> store.texts[index].toString()
        }
    }
}

/** Handle to a tag map owned by a managed FStore */
class FMapHandle(partialHandle: FPartialMapHandle, store: FStore) {
    val bIsValid = true
    val bHasNumberlessKeys = partialHandle.bHasNumberlessKeys
    val store = store
    val num = partialHandle.num
    val pairBegin = partialHandle.pairBegin

    fun getNumberedView(): List<FNumberedPair> {
        check(!bHasNumberlessKeys)
        return store.pairs.asList().subList(pairBegin.toInt(), (pairBegin + num).toInt())
    }

    fun getNumberlessView(): List<FNumberlessPair> {
        check(bHasNumberlessKeys)
        return store.numberlessPairs.asList().subList(pairBegin.toInt(), (pairBegin + num).toInt())
    }

    fun forEachPair(fn: (FNumberedPair) -> Unit) {
        if (bHasNumberlessKeys) {
            getNumberlessView().forEach {
                fn(FNumberedPair(FName.createFromDisplayId(store.nameMap[it.key.value], 0), it.value))
            }
        } else {
            getNumberedView().forEach(fn)
        }
    }
}