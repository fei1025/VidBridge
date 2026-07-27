abstract interface class CredentialStore {
  Future<void> writePassword(String credentialId, String password);
  Future<String?> readPassword(String credentialId);
  Future<void> deletePassword(String credentialId);
}
