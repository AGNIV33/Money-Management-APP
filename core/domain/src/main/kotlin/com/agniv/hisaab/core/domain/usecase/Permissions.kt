package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.domain.model.Role

/**
 * Who may delete an expense or loan (decided 2026-11-10): the person who added it, or a family admin
 * (to clean up someone else's mistake). Same rule is enforced on the server (migration 005).
 */
fun canDelete(
    ownerUserId: String,
    viewerUserId: String,
    viewerRole: Role?,
): Boolean = ownerUserId == viewerUserId || viewerRole == Role.ADMIN
