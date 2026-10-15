package nl.davidengelen.redsquare.chat

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoLocalState
import java.security.MessageDigest

/** Legacy records remain readable. Large histories commit immutable encrypted parts before their atomic index. */
internal class EncryptedChatRecord(private val context:Context, private val name:String,
    private val keyAlias:String, private val family:String) {
    private val index=EncryptedLocalFile(context,name,keyAlias,family)
    private fun part(hash:String)=EncryptedLocalFile(context,"$name.part.$hash",keyAlias,family)

    fun load():EncryptedLocalFile.Contents? = synchronized(SaitoLocalState.lock) {
        val contents=index.load() ?: return@synchronized null
        if(contents.version==1.toByte())return@synchronized contents
        val manifest=JsonParser.parseString(contents.text).asJsonObject
        require(manifest.keySet()==setOf("parts"))
        val hashes=manifest.getAsJsonArray("parts").map{value->
            require(value.isJsonPrimitive&&value.asJsonPrimitive.isString)
            value.asString.also{require(it.matches(Regex("[0-9a-f]{64}")))}
        }
        require(hashes.size>=2)
        val text=buildString {
            hashes.forEach{hash->
                val saved=requireNotNull(part(hash).load())
                require(saved.version==1.toByte()&&saved.text.length<=PART_CHARS&&digest(saved.text)==hash)
                append(saved.text)
            }
        }
        EncryptedLocalFile.Contents(1,text)
    }

    fun save(contents:EncryptedLocalFile.Contents) = synchronized(SaitoLocalState.lock) {
        require(contents.version==1.toByte())
        val retained=mutableSetOf<String>()
        if(contents.text.length<=PART_CHARS)index.save(contents)
        else {
            val hashes=JsonArray()
            var start=0
            while(start<contents.text.length) {
                var end=minOf(start+PART_CHARS,contents.text.length)
                if(end<contents.text.length&&contents.text[end-1].isHighSurrogate())end--
                val text=contents.text.substring(start,end)
                val hash=digest(text)
                val file=part(hash)
                val expected=EncryptedLocalFile.Contents(1,text)
                val existing=file.load()
                if(existing==null)file.save(expected)else check(existing==expected)
                hashes.add(hash);retained.add("$name.part.$hash")
                start=end
            }
            val manifest=JsonObject().apply{add("parts",hashes)}
            index.save(EncryptedLocalFile.Contents(2,manifest.toString()))
        }
        check(load()==contents)
        // Cleanup follows the verified commit. Interrupted writes leave the previous index usable.
        val pattern=Regex("${Regex.escape(name)}\\.part\\.[0-9a-f]{64}(?:\\.bak|\\.new)?")
        context.noBackupFilesDir.listFiles()?.filter{pattern.matches(it.name)}?.forEach{file->
            val base=file.name.removeSuffix(".bak").removeSuffix(".new")
            if(base !in retained&&file.isFile)file.delete()
        }
    }

    companion object {
        private const val PART_CHARS=1024*1024
        private fun digest(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it)}
    }
}
