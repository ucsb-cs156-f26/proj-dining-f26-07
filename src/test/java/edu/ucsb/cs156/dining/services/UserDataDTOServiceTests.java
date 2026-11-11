package edu.ucsb.cs156.dining.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.ucsb.cs156.dining.entities.Admin;
import edu.ucsb.cs156.dining.entities.Moderator;
import edu.ucsb.cs156.dining.entities.User;
import edu.ucsb.cs156.dining.models.UserDTO;
import edu.ucsb.cs156.dining.repositories.AdminRepository;
import edu.ucsb.cs156.dining.repositories.ModeratorRepository;
import edu.ucsb.cs156.dining.repositories.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
public class UserDataDTOServiceTests {

  @Mock UserRepository userRepository;
  @Mock AdminRepository adminRepository;
  @Mock ModeratorRepository moderatorRepository;

  private UserDataDTOService service;

  @BeforeEach
  void setUp() {
    service =
        new UserDataDTOService(
            userRepository, adminRepository, moderatorRepository, " , configured@example.org, ");
  }

  @Test
  void getUserDataDTOs_sortsByIdAndComputesRoles() {

    User u1 = User.builder().id(5L).email("configured@example.org").build();

    User u2 = User.builder().id(6L).email("database-admin@example.org").build();

    User u3 = User.builder().id(7L).email("moderator@example.org").build();

    User u4 = User.builder().id(8L).email("ordinary@example.org").build();

    Pageable requestedPage = PageRequest.of(1, 4, Sort.by("email").descending());

    Pageable expectedPage = PageRequest.of(1, 4, Sort.by("id").ascending());

    Page<User> users = new PageImpl<>(List.of(u1, u2, u3, u4), expectedPage, 12);

    when(userRepository.findAll(expectedPage)).thenReturn(users);

    when(adminRepository.findAll()).thenReturn(List.of(new Admin("database-admin@example.org")));

    when(moderatorRepository.findAll()).thenReturn(List.of(new Moderator("moderator@example.org")));

    Page<UserDTO> result = service.getUserDataDTOs(requestedPage);

    // Verify sorting and repository pagination
    verify(userRepository).findAll(expectedPage);

    // Verify pagination metadata
    assertEquals(1, result.getNumber());
    assertEquals(4, result.getSize());
    assertEquals(12, result.getTotalElements());
    assertEquals(3, result.getTotalPages());
    assertEquals(4, result.getContent().size());

    // Verify ascending user IDs
    assertEquals(List.of(5L, 6L, 7L, 8L), result.getContent().stream().map(UserDTO::id).toList());

    // Configured administrator
    assertTrue(result.getContent().get(0).admin());
    assertFalse(result.getContent().get(0).moderator());

    // Administrator stored in database
    assertTrue(result.getContent().get(1).admin());
    assertFalse(result.getContent().get(1).moderator());

    // Moderator stored in database
    assertFalse(result.getContent().get(2).admin());
    assertTrue(result.getContent().get(2).moderator());

    // Ordinary user
    assertFalse(result.getContent().get(3).admin());
    assertFalse(result.getContent().get(3).moderator());
  }

  @Test
  void getUserDataDTOs_handlesEmptyPage() {

    Pageable pageable = PageRequest.of(0, 10);

    Pageable sortedPageable = PageRequest.of(0, 10, Sort.by("id").ascending());

    when(userRepository.findAll(sortedPageable))
        .thenReturn(new PageImpl<>(List.of(), sortedPageable, 0));

    when(adminRepository.findAll()).thenReturn(List.of());

    when(moderatorRepository.findAll()).thenReturn(List.of());

    Page<UserDTO> result = service.getUserDataDTOs(pageable);

    assertTrue(result.isEmpty());
    assertEquals(0, result.getTotalElements());
    assertEquals(0, result.getTotalPages());

    verify(userRepository).findAll(sortedPageable);
  }

  @Test
  void getUserDataDTOs_ignoresBlankConfiguredAdminEmails() {
    User user = User.builder().id(9L).email("").build();

    Pageable sortedPageable = PageRequest.of(0, 10, Sort.by("id").ascending());

    when(userRepository.findAll(sortedPageable))
        .thenReturn(new PageImpl<>(List.of(user), sortedPageable, 1));

    when(adminRepository.findAll()).thenReturn(List.of());
    when(moderatorRepository.findAll()).thenReturn(List.of());

    Page<UserDTO> result = service.getUserDataDTOs(PageRequest.of(0, 10));

    assertFalse(result.getContent().get(0).admin());
  }
}
