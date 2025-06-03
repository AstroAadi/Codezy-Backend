package com.codeColab.codab.service;

import com.codeColab.codab.entity.UserFile;
import com.codeColab.codab.entity.User;
import com.codeColab.codab.repository.UserFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserFileService {
    @Autowired
    private UserFileRepository userFileRepository;

    public UserFile saveFile(User user, String fileName, String codeContent) {
        UserFile file = userFileRepository.findByUserAndFileName(user, fileName);
        if (file == null) {
            file = new UserFile();
            file.setUser(user);
            file.setFileName(fileName);
        }
        file.setCodeContent(codeContent);
        file.setUpdatedAt(java.time.LocalDateTime.now());
        return userFileRepository.save(file);
    }

    public List<UserFile> getFilesByUser(User user) {
        return userFileRepository.findByUser(user);
    }

    public UserFile getFileByUserAndName(User user, String fileName) {
        return userFileRepository.findByUserAndFileName(user, fileName);
    }
}
