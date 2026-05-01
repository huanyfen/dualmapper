private fun resetRemoteAuthToken() {
    val manager = currentManager
    if (manager is RemoteConnectionManager) {
        manager.resetAuthToken()
        Toast.makeText(application, R.string.reset_token_done, Toast.LENGTH_SHORT).show()
        _uiState.update { it.copy(remoteHostInfo = null) }
    } else {
        Toast.makeText(application, R.string.not_in_remote_mode, Toast.LENGTH_SHORT).show()
    }
}