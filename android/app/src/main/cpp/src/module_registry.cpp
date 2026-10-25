// Recompiled mission registry for the Android host.
//
// The generated module_registry.cpp only lists the module targets that the
// last codegen run emitted. The app owns the complete list instead, so every
// mission shared library can be loaded by the guest loader on demand.

#include <rex/system/kernel_state.h>

namespace {
struct ModuleEntry {
  const char* pe_name;
  const char* guest_path;
  const char* shared_lib_name;
};

// Keep in sync with COD3_MODULES in CMakeLists.txt and the [[modules]] table
// in the codegen manifest.
constexpr ModuleEntry kModules[] = {
    {"blkbrn.dll", "sp/blkbrn/blkbrn.dll", "cod3_pc_blkbrn"},
    {"chambois.dll", "sp/chambois/chambois.dll", "cod3_pc_chambois"},
    {"credits.dll", "sp/credits/credits.dll", "cod3_pc_credits"},
    {"crssrds.dll", "sp/crssrds/crssrds.dll", "cod3_pc_crssrds"},
    {"falaise.dll", "sp/falaise/falaise.dll", "cod3_pc_falaise"},
    {"forest.dll", "sp/forest/forest.dll", "cod3_pc_forest"},
    {"fuelplnt.dll", "sp/fuelplnt/fuelplnt.dll", "cod3_pc_fuelplnt"},
    {"hostage.dll", "sp/hostage/hostage.dll", "cod3_pc_hostage"},
    {"island.dll", "sp/island/island.dll", "cod3_pc_island"},
    {"laison.dll", "sp/laison/laison.dll", "cod3_pc_laison"},
    {"mace2.dll", "sp/mace2/mace2.dll", "cod3_pc_mace2"},
    {"mayenne.dll", "sp/mayenne/mayenne.dll", "cod3_pc_mayenne"},
    {"nightd.dll", "sp/nightd/nightd.dll", "cod3_pc_nightd"},
    {"saint_lo.dll", "sp/saint_lo/saint_lo.dll", "cod3_pc_saint_lo"},
    {"stbert.dll", "sp/stbert/stbert.dll", "cod3_pc_stbert"},
};
}  // namespace

void RegisterRecompiledModules(rex::system::KernelState* kernel_state) {
  for (const auto& module : kModules) {
    kernel_state->RegisterRecompiledModule(module.pe_name, module.guest_path,
                                           module.shared_lib_name);
  }
}
