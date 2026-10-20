package com.addy37.crazyshitadmin

import org.json.JSONObject

/** Small test seam around the unchanged synchronous repository. All calls run on Dispatchers.IO. */
internal interface AdminGateway {
    fun list(token: String): List<AdminRepository.Item>
    fun analytics(token: String): AdminRepository.AnalyticsDashboard
    fun currentConfig(token: String): JSONObject?
    fun configHistory(token: String): List<AdminRepository.ConfigVersion>
    fun validateConfig(token: String, config: JSONObject): Long
    fun publishConfig(token: String, config: JSONObject): Long
    fun rollbackConfig(token: String, version: Long): Long
    fun thread(token: String, id: String): AdminRepository.FeedbackThread
    fun reply(token: String, id: String, status: String, message: String)
    fun updateStatus(token: String, id: String, status: String)
    fun deleteFeedback(token: String, id: String)
}
internal object RepositoryGateway : AdminGateway {
    override fun list(token: String) = AdminRepository.list(token)
    override fun analytics(token: String) = AdminRepository.analytics(token)
    override fun currentConfig(token: String) = AdminRepository.currentConfig(token)
    override fun configHistory(token: String) = AdminRepository.configHistory(token)
    override fun validateConfig(token: String, config: JSONObject) = AdminRepository.validateConfig(token, config)
    override fun publishConfig(token: String, config: JSONObject) = AdminRepository.publishConfig(token, config)
    override fun rollbackConfig(token: String, version: Long) = AdminRepository.rollbackConfig(token, version)
    override fun thread(token: String, id: String) = AdminRepository.thread(token, id)
    override fun reply(token: String, id: String, status: String, message: String) = AdminRepository.reply(token, id, status, message)
    override fun updateStatus(token: String, id: String, status: String) = AdminRepository.updateStatus(token, id, status)
    override fun deleteFeedback(token: String, id: String) = AdminRepository.deleteFeedback(token, id)
}
