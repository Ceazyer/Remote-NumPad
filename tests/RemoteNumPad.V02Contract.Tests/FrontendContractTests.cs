using System;
using System.IO;
using System.Linq;
using Xunit;

namespace RemoteNumPad.V02Contract.Tests;

public sealed class FrontendContractTests
{
    [Fact]
    public void ExcelPanelUsesSemanticCommandsAndChineseFunctionLabels()
    {
        var markup = ReadProjectFile("wwwroot", "index.html");

        Assert.Contains("<title>Excel 录入面板</title>", markup);
        Assert.Contains("data-command=\"PREV_CELL\"", markup);
        Assert.Contains("data-command=\"NEXT_CELL\"", markup);
        Assert.Contains("data-command=\"EDIT\"", markup);
        Assert.Contains("data-command=\"UNDO\"", markup);
        Assert.Contains("data-command=\"COPY\"", markup);
        Assert.Contains("data-command=\"PASTE\"", markup);
        Assert.Contains("Σ 自动求和", markup);
        Assert.DoesNotContain("Ctrl+Z", markup);
        Assert.DoesNotContain("Shift+Tab", markup);
        Assert.DoesNotContain("Alt+=", markup);
    }

    [Fact]
    public void ExcelPanelDeclaresAllSemanticFormulaCommands()
    {
        var markup = ReadProjectFile("wwwroot", "index.html");

        foreach (var command in new[]
        {
            "AUTO_SUM",
            "FORMULA_AVERAGE",
            "FORMULA_MAX",
            "FORMULA_MIN",
            "FORMULA_ROUND",
            "FORMULA_IF"
        })
        {
            Assert.Contains($"data-command=\"{command}\"", markup);
        }
    }

    private static string ReadProjectFile(params string[] pathParts)
    {
        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        while (directory is not null)
        {
            var candidate = Path.Combine(new[] { directory.FullName }.Concat(pathParts).ToArray());
            if (File.Exists(candidate))
            {
                return File.ReadAllText(candidate);
            }

            directory = directory.Parent;
        }

        throw new FileNotFoundException("Project file was not found.", Path.Combine(pathParts));
    }
}
