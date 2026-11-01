package com.siris.voxyvulkan.pack;

import org.lwjgl.opengl.GL45C;

/**
 * GL pass that turns Voxy's reverse-Z depth (near = 1) into the depth shader packs are written for
 * (far = 1), into an R32F texture: what Voxy's own patch rewrite does to depth reads under Iris.
 */
public final class DepthConvert {

    private static final String VERTEX = """
        #version 450 core
        void main() {
            vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
        }
        """;
    private static final String FRAGMENT = """
        #version 450 core
        layout(binding = 0) uniform sampler2D source;
        layout(location = 0) out float depth;
        void main() {
            depth = 1.0 - texelFetch(source, ivec2(gl_FragCoord.xy), 0).r;
        }
        """;

    private static int program;
    private static int vertexArray;
    private static int framebuffer;

    private DepthConvert() {
    }

    /** {@code target} (R32F, same size) = 1 - {@code source} depth. */
    public static void run(int source, int target, int width, int height) {
        if (program == 0) {
            program = GL45C.glCreateProgram();
            int vertex = shader(GL45C.GL_VERTEX_SHADER, VERTEX);
            int fragment = shader(GL45C.GL_FRAGMENT_SHADER, FRAGMENT);
            GL45C.glAttachShader(program, vertex);
            GL45C.glAttachShader(program, fragment);
            GL45C.glLinkProgram(program);
            if (GL45C.glGetProgrami(program, GL45C.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException("Voxy Vulkan depth conversion: " + GL45C.glGetProgramInfoLog(program));
            }
            GL45C.glDeleteShader(vertex);
            GL45C.glDeleteShader(fragment);
            vertexArray = GL45C.glCreateVertexArrays();
            framebuffer = GL45C.glCreateFramebuffers();
        }
        GL45C.glNamedFramebufferTexture(framebuffer, GL45C.GL_COLOR_ATTACHMENT0, target, 0);
        GL45C.glNamedFramebufferDrawBuffer(framebuffer, GL45C.GL_COLOR_ATTACHMENT0);
        GL45C.glBindFramebuffer(GL45C.GL_FRAMEBUFFER, framebuffer);
        GL45C.glViewport(0, 0, width, height);
        GL45C.glDisable(GL45C.GL_DEPTH_TEST);
        GL45C.glDisable(GL45C.GL_STENCIL_TEST);
        GL45C.glDisable(GL45C.GL_BLEND);
        GL45C.glColorMask(true, true, true, true);
        GL45C.glUseProgram(program);
        GL45C.glBindTextureUnit(0, source);
        GL45C.glBindSampler(0, 0);
        GL45C.glBindVertexArray(vertexArray);
        GL45C.glDrawArrays(GL45C.GL_TRIANGLES, 0, 3);
        GL45C.glBindVertexArray(0);
        GL45C.glUseProgram(0);
        GL45C.glBindFramebuffer(GL45C.GL_FRAMEBUFFER, 0);
    }

    private static int shader(int type, String source) {
        int shader = GL45C.glCreateShader(type);
        GL45C.glShaderSource(shader, source);
        GL45C.glCompileShader(shader);
        if (GL45C.glGetShaderi(shader, GL45C.GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException("Voxy Vulkan depth conversion: " + GL45C.glGetShaderInfoLog(shader));
        }
        return shader;
    }
}
