package votl.utils.database.managers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import votl.utils.database.SQLiteDBBase;
import votl.utils.database.DBUtil;

public class GroupManager extends SQLiteDBBase {

    private final String tableMaster = "groupMaster";
    private final String tableSync = "groupSync";
    
    public GroupManager(DBUtil util) {
        super(util);
    }

    // groupMaster table
    public void create(Integer groupId, String guildId, String name) {
        insert(tableMaster, List.of("groupId", "masterId", "name"), List.of(groupId, guildId, name));
    }

    public Integer lastId() {
        Object data = selectLast(tableMaster, "groupId");
        if (data == null) return 0;
        return Integer.parseInt(data.toString());
    }

    public void delete(Integer groupId) {
        delete(tableMaster, "groupId", groupId);
    }

    public void deleteAll(String guildId) {
        delete(tableMaster, "masterId", guildId);
    }

    public void rename(Integer groupId, String name) {
        update(tableMaster, "name", name, "groupId", groupId);
    }

    public String getMaster(Integer groupId) {
        List<Object> data = select(tableMaster, "masterId", "groupId", groupId);
        if (data.isEmpty() || data.get(0) == null) return null;
        return data.get(0).toString();
    }

    public List<Map<String, Object>> getMasterGroups(String masterId) {
        List<Map<String, Object>> data = select(tableMaster, List.of("groupId", "name"), "masterId", masterId);
        if (data.isEmpty() || data.get(0) == null) return Collections.emptyList();
        return data;
    }

    public String getName(Integer groupId) {
        List<Object> data = select(tableMaster, "name", "groupId", groupId);
        if (data.isEmpty() || data.get(0) == null) return null;
        return data.get(0).toString();
    }

    // groupSync table
    public void add(Integer groupId, String guildId) {
        insert(tableSync, List.of("groupId", "guildId"), List.of(groupId, guildId));
    }

    public void remove(Integer groupId, String guildId) {
        delete(tableSync, List.of("groupId", "guildId"), List.of(groupId, guildId));
    }

    public void removeFromGroups(String guildId) {
        delete(tableSync, "guildId", guildId);
    }
    
    public void clearGroup(Integer groupId) {
        delete(tableSync, "groupId", groupId);
    }

    public List<String> getGroupGuildIds(Integer groupId) {
        List<Object> data = select(tableSync, "guildId", "groupId", groupId);
        if (data.isEmpty() || data.get(0) == null) return Collections.emptyList();
        return data.stream().map(obj -> obj.toString()).collect(Collectors.toList());
    }

    public List<Integer> getGuildGroups(String guildId) {
        List<Object> data = select(tableSync, "groupId", "guildId", guildId);
        if (data.isEmpty() || data.get(0) == null) return Collections.emptyList();
        return data.stream().map(obj -> (Integer) obj).collect(Collectors.toList());
    }

    public boolean existSync(Integer groupId, String guildId) {
        if (select(tableSync, "guildId", List.of("groupId", "guildId"), List.of(groupId, guildId)).isEmpty()) {
            return false;
        }
        return true;
    }

}
