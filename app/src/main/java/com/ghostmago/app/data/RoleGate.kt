package com.ghostmago.app.data

/**
 * Role selection gates. Do not expose secrets in UI help text.
 */
object RoleGate {
    fun requiredSecret(role: MeshRole): String? = when (role) {
        MeshRole.SUPREME -> "yaali"
        MeshRole.COMMANDER -> "malk"
        else -> null
    }

    fun needsSecret(role: MeshRole): Boolean = requiredSecret(role) != null

    fun verify(role: MeshRole, input: String): Boolean {
        val need = requiredSecret(role) ?: return true
        return input.trim() == need
    }
}
