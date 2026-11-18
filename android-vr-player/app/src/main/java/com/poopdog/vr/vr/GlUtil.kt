package com.poopdog.vr.vr

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

internal object GlUtil {
    private const val TAG = "VrGl"

    fun floatBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(data); position(0)
        }

    fun shortBuffer(data: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(data.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
            put(data); position(0)
        }

    fun program(vertexSrc: String, fragmentSrc: String): Int {
        val vs = shader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = shader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val status = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw IllegalStateException("Program link failed: $log")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return p
    }

    private fun shader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val status = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            GLES20.glDeleteShader(s)
            throw IllegalStateException("Shader compile failed: $log\n$src")
        }
        return s
    }

    fun externalTexture(): Int {
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return t[0]
    }

    fun texture2d(): Int {
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return t[0]
    }

    fun upload(tex: Int, bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }

    /** Colour-only framebuffer with a texture attachment. Returns [fbo, texture]. */
    fun framebuffer(width: Int, height: Int): IntArray {
        val tex = texture2d()
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        val fb = IntArray(1)
        GLES20.glGenFramebuffers(1, fb, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[0])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, tex, 0)
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) Log.w(TAG, "Framebuffer incomplete: $status")
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        return intArrayOf(fb[0], tex)
    }

    fun deleteFramebuffer(fbAndTex: IntArray) {
        GLES20.glDeleteFramebuffers(1, intArrayOf(fbAndTex[0]), 0)
        GLES20.glDeleteTextures(1, intArrayOf(fbAndTex[1]), 0)
    }
}

/** A mesh uploaded to client-side direct buffers, drawable with any of our programs. */
internal class GlMesh(val stride: Int, vertices: FloatArray, indices: ShortArray) {
    val vb: FloatBuffer = GlUtil.floatBuffer(vertices)
    val ib: ShortBuffer = GlUtil.shortBuffer(indices)
    val count = indices.size

    /** Binds [posSize] position floats then 2 texcoord floats. */
    fun draw(aPos: Int, aUv: Int, posSize: Int) {
        val strideBytes = stride * 4
        vb.position(0)
        GLES20.glVertexAttribPointer(aPos, posSize, GLES20.GL_FLOAT, false, strideBytes, vb)
        GLES20.glEnableVertexAttribArray(aPos)
        vb.position(posSize)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, strideBytes, vb)
        GLES20.glEnableVertexAttribArray(aUv)
        ib.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, count, GLES20.GL_UNSIGNED_SHORT, ib)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUv)
    }
}
