package com.example.securemcp;

import java.util.Map;
import java.util.Optional;

public interface UserDataRepository {

    Optional<Map<String, Object>> findByPrincipalId(String principalId);
}
