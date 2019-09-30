package com.sksamuel.avro4k

import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.ElementValueDecoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PrimitiveKind
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.SerializationException
import kotlinx.serialization.StructureKind
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.avro.util.Utf8
import java.nio.ByteBuffer

class RecordDecoder(val record: GenericRecord) : ElementValueDecoder() {

   private var currentDesc: SerialDescriptor? = null
   private var currentIndex = -1
   
   override fun beginStructure(desc: SerialDescriptor, vararg typeParams: KSerializer<*>): CompositeDecoder {
      println("beginStructure $desc")
      if (currentIndex == -1) {
         return this
      }
      return when (desc.kind as StructureKind) {
         // if we have a class and the current tag is null, then we are in the "root" class and just use "this" decoder
         // otherwise we'll recurse into a fresh ClassDecoder
         StructureKind.CLASS -> RecordDecoder(record[currentIndex] as GenericRecord)
         StructureKind.MAP -> this
         StructureKind.LIST -> {
            val decoder: CompositeDecoder = if (desc.getElementDescriptor(1).kind == PrimitiveKind.BYTE) {
               when (val data = record[currentIndex]) {
                  is List<*> -> ByteArrayDecoder((data as List<Byte>).toByteArray())
                  is Array<*> -> ByteArrayDecoder((data as Array<Byte>).toByteArray())
                  is ByteArray -> ByteArrayDecoder(data)
                  is ByteBuffer -> ByteArrayDecoder(data.array())
                  else -> this
               }
            } else {
               when (val data = record[currentIndex]) {
                  is List<*> -> ListDecoder(data)
                  is Array<*> -> ListDecoder(data.asList())
                  else -> this
               }
            }
            decoder
         }
      }
   }

   private fun resolvedName(): String {
      // the tag is the name of the field in the data class, but the record can have the field
      // stored under another name defined by @AvroName
      // so we must look up the name defined by the annotation from the descriptor annotations
      val naming = NameExtractor(currentDesc!!, currentIndex - 1)
      return naming.name()
   }


//   override fun decodeTaggedEnum(tag: String, enumDescription: EnumDescriptor): Int {
//      val symbol = when (val v = record.get(resolvedName())) {
//         is GenericEnumSymbol<*> -> v.toString()
//         is String -> v
//         else -> v.toString()
//      }
//      return (0 until enumDescription.elementsCount).find { enumDescription.getElementName(it) == symbol } ?: -1
//   }

   override fun decodeString(): String {
      return when (val v = record[currentIndex]) {
         is String -> v
         is Utf8 -> v.toString()
         is GenericData.Fixed -> String(v.bytes())
         is ByteArray -> String(v)
         is CharSequence -> v.toString()
         is ByteBuffer -> String(v.array())
         null -> throw SerializationException("Cannot decode <null> as a string")
         else -> throw SerializationException("Unsupported type for String ${v.javaClass}")
      }
   }

   override fun decodeBoolean(): Boolean {
      return when (val v = record[currentIndex]) {
         is Boolean -> v
         null -> throw SerializationException("Cannot decode <null> as a Boolean")
         else -> throw SerializationException("Unsupported type for Boolean ${v.javaClass}")
      }
   }

   override fun decodeByte(): Byte {
      return when (val v = record[currentIndex]) {
         is Byte -> v
         is Int -> if (v < 255) v.toByte() else throw SerializationException("Out of bound integer cannot be converted to byte [$v]")
         null -> throw SerializationException("Cannot decode <null> as a Byte")
         else -> throw SerializationException("Unsupported type for Byte ${v.javaClass}")
      }
   }

   override fun decodeFloat(): Float {
      return when (val v = record[currentIndex]) {
         is Float -> v
         null -> throw SerializationException("Cannot decode <null> as a Float")
         else -> throw SerializationException("Unsupported type for Float ${v.javaClass}")
      }
   }

   override fun decodeInt(): Int {
      return when (val v = record[currentIndex]) {
         is Int -> v
         null -> throw SerializationException("Cannot decode <null> as a Int")
         else -> throw SerializationException("Unsupported type for Int ${v.javaClass}")
      }
   }

   override fun decodeLong(): Long {
      return when (val v = record[currentIndex]) {
         is Long -> v
         is Int -> v.toLong()
         null -> throw SerializationException("Cannot decode <null> as a Long")
         else -> throw SerializationException("Unsupported type for Long ${v.javaClass}")
      }
   }

   override fun decodeDouble(): Double {
      return when (val v = record[currentIndex]) {
         is Double -> v
         is Float -> v.toDouble()
         null -> throw SerializationException("Cannot decode <null> as a Double")
         else -> throw SerializationException("Unsupported type for Double ${v.javaClass}")
      }
   }

   override fun decodeElementIndex(desc: SerialDescriptor): Int {
      println("decodeElementIndex $desc $currentIndex")
      currentIndex++
      while (currentIndex < desc.elementsCount) {
         if (desc.isElementOptional(currentIndex)) {
            currentIndex++
         } else {
            return currentIndex
         }
      }
      return CompositeDecoder.READ_DONE
   }
}

class ByteArrayDecoder(val data: ByteArray) : ElementValueDecoder() {

   private var index = 0

   override fun decodeCollectionSize(desc: SerialDescriptor): Int = data.size

   override fun decodeByte(): Byte {
      return data[index++]
   }
}

class ListDecoder(private val array: List<Any?>) : ElementValueDecoder() {

   private var index = 0

   init {
      println(array)
   }

   override fun decodeBoolean(): Boolean {
      return array[index++] as Boolean
   }

   override fun decodeLong(): Long {
      return array[index++] as Long
   }

   override fun decodeString(): String {
      return array[index++] as String
   }

   override fun decodeDouble(): Double {
      return array[index++] as Double
   }

   override fun decodeFloat(): Float {
      return array[index++] as Float
   }

   override fun decodeByte(): Byte {
      return array[index++] as Byte
   }

   override fun beginStructure(desc: SerialDescriptor, vararg typeParams: KSerializer<*>): CompositeDecoder {
      println("beginStructure $desc")
      return when (desc.kind as StructureKind) {
         StructureKind.CLASS -> RecordDecoder(array[index++] as GenericRecord)
         StructureKind.MAP, StructureKind.LIST -> this
      }
   }

   override fun decodeCollectionSize(desc: SerialDescriptor): Int = array.size
}