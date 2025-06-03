package com.codeColab.codab.repository;

import com.codeColab.codab.entity.UserFile;
import com.codeColab.codab.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserFileRepository extends JpaRepository<UserFile, Long> {
    List<UserFile> findByUser(User user);
    UserFile findByUserAndFileName(User user, String fileName);
}