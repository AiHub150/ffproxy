package com.ffproxy

object EntityStore {
    data class Entity(var x: Float, var y: Float, var z: Float, var hp: Float)
    val entities = arrayOfNulls<Entity>(64)
    var count = 0

    fun update(idx: Int, x: Float, y: Float, z: Float, hp: Float) {
        if (idx < 0 || idx >= 64) return
        entities[idx] = Entity(x, y, z, hp)
    }

    fun reset() {
        for (i in entities.indices) entities[i] = null
        count = 0
    }
}
