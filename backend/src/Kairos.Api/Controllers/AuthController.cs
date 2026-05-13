using Kairos.Api.Contracts;
using Kairos.Api.Data;
using Kairos.Api.Data.Entities;
using Kairos.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using System.Security.Claims;

namespace Kairos.Api.Controllers;

[ApiController]
[Route("api/auth")]
public sealed class AuthController(
    UserManager<ApplicationUser> userManager,
    AppDbContext dbContext,
    AuthTokenService tokenService) : ControllerBase
{
    [HttpPost("register")]
    [AllowAnonymous]
    public async Task<ActionResult<AuthResponse>> Register(RegisterRequest request, CancellationToken cancellationToken)
    {
        var email = request.Email.Trim().ToLowerInvariant();
        if (string.IsNullOrWhiteSpace(email) || string.IsNullOrWhiteSpace(request.Password))
        {
            return BadRequest(new { error = "Email and password are required." });
        }

        var user = new ApplicationUser
        {
            UserName = email,
            Email = email,
            EmailConfirmed = true
        };
        var result = await userManager.CreateAsync(user, request.Password);
        if (!result.Succeeded)
        {
            return BadRequest(new { errors = result.Errors.Select(error => error.Description) });
        }

        return await CreateAuthResponse(user, request.DeviceName, cancellationToken);
    }

    [HttpPost("login")]
    [AllowAnonymous]
    public async Task<ActionResult<AuthResponse>> Login(LoginRequest request, CancellationToken cancellationToken)
    {
        var email = request.Email.Trim().ToLowerInvariant();
        var user = await userManager.FindByEmailAsync(email);
        if (user is null || !await userManager.CheckPasswordAsync(user, request.Password))
        {
            return Unauthorized(new { error = "Invalid email or password." });
        }

        return await CreateAuthResponse(user, request.DeviceName, cancellationToken);
    }

    [HttpPost("refresh")]
    [AllowAnonymous]
    public async Task<ActionResult<AuthResponse>> Refresh(RefreshRequest request, CancellationToken cancellationToken)
    {
        var tokenHash = AuthTokenService.HashRefreshToken(request.RefreshToken);
        var storedToken = await dbContext.RefreshTokens
            .Include(token => token.User)
            .SingleOrDefaultAsync(token => token.TokenHash == tokenHash, cancellationToken);
        if (storedToken is null || !storedToken.IsActive)
        {
            return Unauthorized(new { error = "Invalid refresh token." });
        }

        storedToken.RevokedAt = DateTimeOffset.UtcNow;
        var response = await CreateAuthResponse(storedToken.User, request.DeviceName ?? storedToken.DeviceName, cancellationToken);
        storedToken.ReplacedByTokenId = await dbContext.RefreshTokens
            .Where(token => token.UserId == storedToken.UserId && token.RevokedAt == null)
            .OrderByDescending(token => token.CreatedAt)
            .Select(token => token.Id)
            .FirstAsync(cancellationToken);
        await dbContext.SaveChangesAsync(cancellationToken);
        return response;
    }

    [HttpPost("logout")]
    [AllowAnonymous]
    public async Task<IActionResult> Logout(LogoutRequest request, CancellationToken cancellationToken)
    {
        var tokenHash = AuthTokenService.HashRefreshToken(request.RefreshToken);
        var storedToken = await dbContext.RefreshTokens
            .SingleOrDefaultAsync(token => token.TokenHash == tokenHash, cancellationToken);
        if (storedToken is not null && storedToken.RevokedAt is null)
        {
            storedToken.RevokedAt = DateTimeOffset.UtcNow;
            await dbContext.SaveChangesAsync(cancellationToken);
        }

        return NoContent();
    }

    [HttpGet("me")]
    [Authorize]
    public async Task<ActionResult<UserResponse>> Me()
    {
        var userId = GetCurrentUserId();
        var user = await userManager.FindByIdAsync(userId.ToString());
        return user is null
            ? Unauthorized()
            : new UserResponse(user.Id, user.Email ?? string.Empty);
    }

    private async Task<AuthResponse> CreateAuthResponse(
        ApplicationUser user,
        string? deviceName,
        CancellationToken cancellationToken)
    {
        var accessToken = tokenService.CreateAccessToken(user);
        var refreshToken = tokenService.CreateRefreshToken();
        dbContext.RefreshTokens.Add(new RefreshToken
        {
            UserId = user.Id,
            TokenHash = refreshToken.TokenHash,
            DeviceName = string.IsNullOrWhiteSpace(deviceName) ? null : deviceName.Trim(),
            ExpiresAt = refreshToken.ExpiresAt
        });
        await dbContext.SaveChangesAsync(cancellationToken);

        return new AuthResponse(
            accessToken.Token,
            accessToken.ExpiresAt,
            refreshToken.RawToken,
            refreshToken.ExpiresAt,
            new UserResponse(user.Id, user.Email ?? string.Empty));
    }

    private Guid GetCurrentUserId()
    {
        var value = User.FindFirstValue(ClaimTypes.NameIdentifier)
            ?? User.FindFirstValue("sub")
            ?? throw new InvalidOperationException("Authenticated user id claim is missing.");
        return Guid.Parse(value);
    }
}
