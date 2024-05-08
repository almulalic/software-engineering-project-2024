package ba.edu.ibu.eventport.auth.core.service;

import ba.edu.ibu.eventport.auth.core.model.RefreshToken;
import ba.edu.ibu.eventport.auth.core.model.User;
import ba.edu.ibu.eventport.auth.core.model.enums.AuthType;
import ba.edu.ibu.eventport.auth.core.model.enums.Role;
import ba.edu.ibu.eventport.auth.core.repository.UserRepository;
import ba.edu.ibu.eventport.auth.exception.auth.TokenRefreshException;
import ba.edu.ibu.eventport.auth.exception.auth.UnauthorizedException;
import ba.edu.ibu.eventport.auth.exception.repository.UserExistsException;
import ba.edu.ibu.eventport.auth.exception.repository.UserNotFoundException;
import ba.edu.ibu.eventport.auth.rest.models.dto.CreateUserRequest;
import ba.edu.ibu.eventport.auth.rest.models.dto.token.GenerateTokenRequest;
import ba.edu.ibu.eventport.auth.rest.models.dto.token.RefreshTokenRequest;
import ba.edu.ibu.eventport.auth.rest.models.dto.token.RefreshTokenResponse;
import ba.edu.ibu.eventport.auth.rest.models.dto.token.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service class for managing authentication.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

  private final PasswordEncoder passwordEncoder;
  private final JWTService jwtService;
  private final AuthenticationManager authenticationManager;
  private final UserRepository userRepository;
  private final RefreshTokenService refreshTokenService;

  /**
   * Creates a new user.
   *
   * @param dto The user creation request.
   * @return The created user.
   */
  public User createUser(CreateUserRequest dto) {
    if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
      throw new UserExistsException("User with this email already exists");
    }

    User user = dto.toEntity();
    user.setAuthType(AuthType.PLAIN);
    user.setAssignedRoles(List.of(Role.GUEST, Role.ATTENDEE));
    user.setPassword(passwordEncoder.encode(dto.getPassword()));

    return userRepository.save(user);
  }

  /**
   * Generates authentication tokens.
   *
   * @param dto The token generation request.
   * @return The token response.
   */
  public TokenResponse generateToken(GenerateTokenRequest dto) {
    try {
      authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword())
      );
    } catch (BadCredentialsException ex) {
      throw new UnauthorizedException("Username or password is incorrect!");
    }

    User user = userRepository
                  .findByEmail(dto.getEmail())
                  .orElseThrow(() -> new UserNotFoundException(
                    String.format("User with email '%s' not found.", dto.getEmail())
                  ));

    return TokenResponse.Builder()
             .withUser(user)
             .withAccessToken(jwtService.generateToken(user))
             .withRefreshToken(
               dto.isRememberMe() ? refreshTokenService.createRefreshToken(user.getId()).getToken() : null)
             .build();
  }

  /**
   * Refreshes authentication tokens.
   *
   * @param dto The token refresh request.
   * @return The refresh token response.
   */
  public RefreshTokenResponse refreshToken(RefreshTokenRequest dto) {
    String requestRefreshToken = dto.getRefreshToken();

    return refreshTokenService.findByToken(requestRefreshToken)
             .map(refreshTokenService::verifyExpiration)
             .map(RefreshToken::getUser)
             .map(user -> RefreshTokenResponse.Builder()
                            .withAccessToken(jwtService.generateToken(user))
                            .withRefreshToken(requestRefreshToken)
                            .build())
             .orElseThrow(() -> new TokenRefreshException(requestRefreshToken,
               "Refresh token is not in database!"));
  }
}
