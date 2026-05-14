using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Kairos.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddTaskClientId : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<Guid>(
                name: "ClientId",
                table: "tasks",
                type: "uuid",
                nullable: true);

            migrationBuilder.Sql(@"UPDATE tasks SET ""ClientId"" = ""Id"" WHERE ""ClientId"" IS NULL;");

            migrationBuilder.AlterColumn<Guid>(
                name: "ClientId",
                table: "tasks",
                type: "uuid",
                nullable: false,
                oldClrType: typeof(Guid),
                oldType: "uuid",
                oldNullable: true);

            migrationBuilder.CreateIndex(
                name: "IX_tasks_UserId_ClientId",
                table: "tasks",
                columns: new[] { "UserId", "ClientId" },
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "IX_tasks_UserId_ClientId",
                table: "tasks");

            migrationBuilder.DropColumn(
                name: "ClientId",
                table: "tasks");
        }
    }
}
