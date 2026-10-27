package ru.anyforms.service.mcp;

import org.springframework.stereotype.Component;
import ru.anyforms.dto.mcp.McpToolDescriptor;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class McpToolRegistry {

    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    public McpToolRegistry(List<McpTool> tools) {
        tools.stream()
                .sorted(Comparator.comparing(McpTool::name))
                .forEach(tool -> {
                    if (this.tools.putIfAbsent(tool.name(), tool) != null) {
                        throw new IllegalStateException("Два MCP-инструмента с именем " + tool.name());
                    }
                });
    }

    public List<McpToolDescriptor> descriptors() {
        return tools.values().stream().map(McpTool::descriptor).toList();
    }

    public Optional<McpTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }
}
