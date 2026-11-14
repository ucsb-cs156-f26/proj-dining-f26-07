package edu.ucsb.cs156.dining.services;

import edu.ucsb.cs156.dining.entities.Admin;
import edu.ucsb.cs156.dining.entities.Moderator;
import edu.ucsb.cs156.dining.entities.User;
import edu.ucsb.cs156.dining.models.UserDTO;
import edu.ucsb.cs156.dining.repositories.AdminRepository;
import edu.ucsb.cs156.dining.repositories.ModeratorRepository;
import edu.ucsb.cs156.dining.repositories.UserRepository;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class UserDataDTOService {

  private final UserRepository userRepository;
  private final AdminRepository adminRepository;
  private final ModeratorRepository moderatorRepository;
  private final Set<String> configuredAdminEmails;

  public UserDataDTOService(
      UserRepository userRepository,
      AdminRepository adminRepository,
      ModeratorRepository moderatorRepository,
      @Value("${app.admin.emails:}") String adminEmails) {

    this.userRepository = userRepository;
    this.adminRepository = adminRepository;
    this.moderatorRepository = moderatorRepository;

    this.configuredAdminEmails =
        Arrays.stream(adminEmails.split(","))
            .map(String::trim)
            .filter(email -> !email.isEmpty())
            .collect(Collectors.toSet());
  }

  public Page<UserDTO> getUserDataDTOs(Pageable pageable) {

    Pageable sortedPageable =
        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id").ascending());

    Page<User> users = userRepository.findAll(sortedPageable);

    Set<String> adminEmails =
        StreamSupport.stream(adminRepository.findAll().spliterator(), false)
            .map(Admin::getEmail)
            .collect(Collectors.toSet());

    Set<String> moderatorEmails =
        StreamSupport.stream(moderatorRepository.findAll().spliterator(), false)
            .map(Moderator::getEmail)
            .collect(Collectors.toSet());

    return users.map(
        user -> {
          String email = user.getEmail();

          boolean isAdmin = configuredAdminEmails.contains(email) || adminEmails.contains(email);

          boolean isModerator = moderatorEmails.contains(email);

          return new UserDTO(user, isAdmin, isModerator);
        });
  }
}
