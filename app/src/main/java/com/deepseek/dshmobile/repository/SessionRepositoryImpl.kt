package com.deepseek.dshmobile.repository

import com.deepseek.dshmobile.database.AppDatabase
import com.deepseek.dshmobile.database.Message
import com.deepseek.dshmobile.database.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val database: AppDatabase
) : SessionRepository {

    override fun getAllSessions(): Flow<List<SessionEntity>> =
        database.sessionDao().getAllSessions()

    override suspend fun getSession(sessionId: String): SessionEntity? =
        database.sessionDao().getSession(sessionId)

    override suspend fun createSession(name: String): String {
        val sessionId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val session = SessionEntity(sessionId, name, now, now)
        database.sessionDao().insertSession(session)
        return sessionId
    }

    override suspend fun updateSession(sessionId: String, name: String) {
        val session = database.sessionDao().getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        database.sessionDao().updateSession(session.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteSession(sessionId: String) {
        database.sessionDao().deleteSessionById(sessionId)
    }

    override suspend fun saveMessage(sessionId: String, role: String, content: String) {
        val message = Message(
            messageId = java.util.UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            timestamp = System.currentTimeMillis()
        )
        database.messageDao().insertMessage(message)
        // 更新会话最后活动时间
        val session = database.sessionDao().getSession(sessionId)
        session?.let {
            database.sessionDao().updateSession(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    override fun getMessages(sessionId: String): Flow<List<Message>> =
        database.messageDao().getMessages(sessionId)
}
