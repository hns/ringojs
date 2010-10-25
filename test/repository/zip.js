var {join} = require("fs");

importPackage(org.ringojs.repository);
var path = join(module.directory, "data.zip");
var repo = new ZipRepository(path);
repo.setModuleRoot(true);

require("./common").setup(exports, "", repo);

if (require.main == module.id) {
    require("test").run(exports);
}
