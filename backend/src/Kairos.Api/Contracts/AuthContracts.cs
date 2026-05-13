namespace Kairos.Api.Contracts;

public sealed record RegisterRequest(string Email, string Password, string? DeviceName);
public sealed record LoginRequest(string Email, string Password, string? DeviceName);
public sealed record RefreshRequest(string RefreshToken, string? DeviceName);
public sealed record LogoutRequest(string RefreshToken);
public sealed record UserResponse(Guid Id, string Email);
public sealed record AuthResponse(
    string AccessToken,
    DateTimeOffset AccessTokenExpiresAt,
    string RefreshToken,
    DateTimeOffset RefreshTokenExpiresAt,
    UserResponse User);
