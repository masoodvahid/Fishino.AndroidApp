package rdsco.fishino.app.ui.shell

/** The signed-in screens; [route] is how the back stack is saved. */
sealed interface Screen {
    val route: String

    /** Screens of the side menu; the others open on top of one of them. */
    val isRoot: Boolean get() = false

    data object Home : Screen {
        override val route = "home"
        override val isRoot = true
    }

    data object Documents : Screen {
        override val route = "documents"
        override val isRoot = true
    }

    data object Requests : Screen {
        override val route = "requests"
        override val isRoot = true
    }

    data object News : Screen {
        override val route = "news"
        override val isRoot = true
    }

    data object Profile : Screen {
        override val route = "profile"
        override val isRoot = true
    }

    /** Opened from the menu's envelope and from phone notifications. */
    data object Inbox : Screen {
        override val route = "inbox"
        override val isRoot = true
    }

    data class DocumentDetail(val id: Long) : Screen {
        override val route = "documents/$id"
    }

    data class NewsDetail(val id: Long) : Screen {
        override val route = "news/$id"
    }

    data class RequestDetail(val id: Long) : Screen {
        override val route = "requests/$id"
    }

    data object Forms : Screen {
        override val route = "forms"
    }

    data class FormFill(val id: Long) : Screen {
        override val route = "forms/$id"
    }

    data class ProfileSection(val tab: String) : Screen {
        override val route = "profile/$tab"
    }

    companion object {
        val roots: List<Screen> = listOf(Home, Documents, Requests, News, Profile, Inbox)

        fun parse(route: String): Screen? {
            roots.firstOrNull { it.route == route }?.let { return it }
            if (route == Forms.route) return Forms

            val (head, tail) = route.split('/', limit = 2).let { it[0] to it.getOrNull(1) }
            tail ?: return null

            return when (head) {
                "news" -> tail.toLongOrNull()?.let(::NewsDetail)
                "requests" -> tail.toLongOrNull()?.let(::RequestDetail)
                "documents" -> tail.toLongOrNull()?.let(::DocumentDetail)
                "forms" -> tail.toLongOrNull()?.let(::FormFill)
                "profile" -> ProfileSection(tail)
                else -> null
            }
        }
    }
}
