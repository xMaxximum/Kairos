using Kairos.Api.Data.Entities;
using Microsoft.AspNetCore.Identity.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore;

namespace Kairos.Api.Data;

public sealed class AppDbContext(DbContextOptions<AppDbContext> options)
    : IdentityDbContext<ApplicationUser, Microsoft.AspNetCore.Identity.IdentityRole<Guid>, Guid>(options)
{
    public DbSet<TaskItem> Tasks => Set<TaskItem>();
    public DbSet<NoteItem> Notes => Set<NoteItem>();
    public DbSet<NoteFolder> NoteFolders => Set<NoteFolder>();
    public DbSet<TagItem> Tags => Set<TagItem>();
    public DbSet<NoteTagLink> NoteTagLinks => Set<NoteTagLink>();
    public DbSet<TaskTagLink> TaskTagLinks => Set<TaskTagLink>();
    public DbSet<NoteTaskReference> NoteTaskReferences => Set<NoteTaskReference>();
    public DbSet<NoteNoteReference> NoteNoteReferences => Set<NoteNoteReference>();
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();

    protected override void OnModelCreating(ModelBuilder builder)
    {
        base.OnModelCreating(builder);

        builder.Entity<TaskItem>(entity =>
        {
            entity.ToTable("tasks");
            entity.HasKey(task => task.Id);
            entity.Property(task => task.ClientId).IsRequired();
            entity.Property(task => task.Title).HasMaxLength(240).IsRequired();
            entity.Property(task => task.Description).HasDefaultValue(string.Empty);
            entity.Property(task => task.Recurrence).HasMaxLength(32).HasDefaultValue("NONE");
            entity.HasIndex(task => new { task.UserId, task.ClientId }).IsUnique();
            entity.HasIndex(task => new { task.UserId, task.UpdatedAt });
            entity.HasIndex(task => new { task.UserId, task.DeletedAt });
            entity.HasOne(task => task.User)
                .WithMany()
                .HasForeignKey(task => task.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        builder.Entity<RefreshToken>(entity =>
        {
            entity.ToTable("refresh_tokens");
            entity.HasKey(token => token.Id);
            entity.Property(token => token.TokenHash).HasMaxLength(128).IsRequired();
            entity.Property(token => token.DeviceName).HasMaxLength(160);
            entity.HasIndex(token => token.TokenHash).IsUnique();
            entity.HasIndex(token => new { token.UserId, token.RevokedAt, token.ExpiresAt });
            entity.HasOne(token => token.User)
                .WithMany()
                .HasForeignKey(token => token.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        builder.Entity<NoteItem>(entity =>
        {
            entity.ToTable("notes");
            entity.HasKey(note => note.Id);
            entity.Property(note => note.ClientId).IsRequired();
            entity.Property(note => note.Title).HasMaxLength(240).IsRequired();
            entity.Property(note => note.MarkdownBody).HasDefaultValue(string.Empty);
            entity.HasIndex(note => new { note.UserId, note.ClientId }).IsUnique();
            entity.HasIndex(note => new { note.UserId, note.UpdatedAt });
            entity.HasIndex(note => new { note.UserId, note.DeletedAt });
            entity.HasIndex(note => new { note.UserId, note.FolderClientId });
            entity.HasOne(note => note.User)
                .WithMany()
                .HasForeignKey(note => note.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        builder.Entity<NoteFolder>(entity =>
        {
            entity.ToTable("note_folders");
            entity.HasKey(folder => folder.Id);
            entity.Property(folder => folder.ClientId).IsRequired();
            entity.Property(folder => folder.Name).HasMaxLength(160).IsRequired();
            entity.HasIndex(folder => new { folder.UserId, folder.ClientId }).IsUnique();
            entity.HasIndex(folder => new { folder.UserId, folder.ParentClientId });
            entity.HasIndex(folder => new { folder.UserId, folder.UpdatedAt });
            entity.HasOne(folder => folder.User)
                .WithMany()
                .HasForeignKey(folder => folder.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        builder.Entity<TagItem>(entity =>
        {
            entity.ToTable("tags");
            entity.HasKey(tag => tag.Id);
            entity.Property(tag => tag.ClientId).IsRequired();
            entity.Property(tag => tag.Name).HasMaxLength(80).IsRequired();
            entity.Property(tag => tag.NormalizedName).HasMaxLength(80).IsRequired();
            entity.HasIndex(tag => new { tag.UserId, tag.ClientId }).IsUnique();
            entity.HasIndex(tag => new { tag.UserId, tag.NormalizedName }).IsUnique();
            entity.HasOne(tag => tag.User)
                .WithMany()
                .HasForeignKey(tag => tag.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        builder.Entity<NoteTagLink>(entity =>
        {
            entity.ToTable("note_tag_links");
            entity.HasKey(link => link.Id);
            entity.HasIndex(link => new { link.UserId, link.ClientId }).IsUnique();
            entity.HasIndex(link => new { link.UserId, link.NoteClientId, link.TagClientId }).IsUnique();
            entity.HasOne(link => link.User)
                .WithMany()
                .HasForeignKey(link => link.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        builder.Entity<TaskTagLink>(entity =>
        {
            entity.ToTable("task_tag_links");
            entity.HasKey(link => link.Id);
            entity.HasIndex(link => new { link.UserId, link.ClientId }).IsUnique();
            entity.HasIndex(link => new { link.UserId, link.TaskClientId, link.TagClientId }).IsUnique();
            entity.HasOne(link => link.User)
                .WithMany()
                .HasForeignKey(link => link.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        builder.Entity<NoteTaskReference>(entity =>
        {
            entity.ToTable("note_task_references");
            entity.HasKey(reference => reference.Id);
            entity.HasIndex(reference => new { reference.UserId, reference.NoteClientId, reference.TaskClientId }).IsUnique();
            entity.HasOne(reference => reference.User)
                .WithMany()
                .HasForeignKey(reference => reference.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        builder.Entity<NoteNoteReference>(entity =>
        {
            entity.ToTable("note_note_references");
            entity.HasKey(reference => reference.Id);
            entity.HasIndex(reference => new { reference.UserId, reference.SourceNoteClientId, reference.TargetNoteClientId }).IsUnique();
            entity.HasOne(reference => reference.User)
                .WithMany()
                .HasForeignKey(reference => reference.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }
}
