package votl.utils.database.managers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import votl.utils.database.DBBase;
import votl.utils.database.DBUtil;

public class GroupManager extends DBBase {
    
    public GroupManager(DBUtil util) {
        super(util);
    }

    // groupMaster table
    public void create(Integer groupId, String guildId, String name) {
        insert("groupMaster", List.of("groupId", "masterId", "name"), List.of(groupId, guildId, name));
    }

    public Integer lastId() {
        Object data = selectLast("groupMaster", "groupId");
        if (data == null) {
            return 0;
        }
        return Integer.parseInt(data.toString());
    }

    public void delete(Integer groupId) {
        delete("groupMaster", "groupId", groupId);
    }

    public void deleteAll(String guildId) {
        delete("groupMaster", "guildId", guildId);
    }

    public void rename(Integer groupId, String name) {
        update("groupMaster", "name", name, "groupId", groupId);
    }

    public String getMaster(Integer groupId) {
        List<Object> data = select("groupMaster", "masterId", "groupId", groupId);
        if (data.isEmpty() || data.get(0) == null) {
            return null;
        }
        return data.get(0).toString();
    }

    public List<Map<String, Object>> getMasterGroups(String masterId) {
        List<Map<String, Object>> data = select("groupMaster", List.of("groupId", "name"), "masterId", masterId);
        if (data.isEmpty() || data.get(0) == null) {
            return Collections.emptyList();
        }
        return data;
    }

    public String getName(Integer groupId) {
        List<Object> data = select("groupMaster", "name", "groupId", groupId);
        if (data.isEmpty() || data.get(0) == null) {
            return null;
        }
        return data.get(0).toString();
    }

    // groupSync table
    public void add(Integer groupId, String guildId) {
        insert("groupSync", List.of("groupId", "guildId"), List.of(groupId, guildId));
    }

    public void remove(Integer groupId, String guildId) {
        delete("groupSync", List.of("groupId", "guildId"), List.of(groupId, guildId));
    }

    public void removeAll(String guildId) {
        delete("groupSync", "guildId", guildId);
    }

    public List<String> getGroupGuildIds(Integer groupId) {
        List<Object> data = select("groupSync", "guildId", "groupId", groupId);
        if (data.isEmpty() || data.get(0) == null) {
            return Collections.emptyList();
        }
        return data.stream().map(obj -> obj.toString()).collect(Collectors.toList());
    }

    public List<Integer> getGuildGroups(String guildId) {
        List<Object> data = select("groupSync", "groupId", "guildId", guildId);
        if (data.isEmpty() || data.get(0) == null) {
            return Collections.emptyList();
        }
        return data.stream().map(obj -> (Integer) obj).collect(Collectors.toList());
    }

    public boolean existSync(Integer groupId, String guildId) {
        if (select("groupSync", "guildId", List.of("groupId", "guildId"), List.of(groupId, guildId)).isEmpty()) {
            return false;
        }
        return true;
    }

}
