const express = require("express");
const router = express.Router();

const {
  getQueries,
  getQueryById,
  createQuery,
  patchQuery,
  deleteQuery,
  searchQuery,
} = require("../controllers/queryController");

router.get("/", getQueries);

// Search route MUST come before /:id
router.get("/search", searchQuery);

router.get("/:id", getQueryById);

router.post("/", createQuery);

router.patch("/:id", patchQuery);

router.delete("/:id", deleteQuery);

module.exports = router;